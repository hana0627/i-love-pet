package hana.lovepet.orderservice.api.service.impl

import hana.lovepet.orderservice.api.controller.dto.request.CreateOrderRequest
import hana.lovepet.orderservice.api.controller.dto.request.CreateOrderItemRequest
import hana.lovepet.orderservice.api.controller.dto.request.OrderSearchCondition
import hana.lovepet.orderservice.api.controller.dto.request.ConfirmOrderRequest
import hana.lovepet.orderservice.api.controller.dto.request.FailOrderRequest
import hana.lovepet.orderservice.api.controller.dto.response.GetOrderItemsResponse
import hana.lovepet.orderservice.api.controller.dto.response.GetOrdersResponse
import hana.lovepet.orderservice.api.controller.dto.response.ConfirmOrderResponse
import hana.lovepet.orderservice.api.controller.dto.response.PrepareOrderResponse
import hana.lovepet.orderservice.api.domain.Order
import hana.lovepet.orderservice.api.domain.OrderItem
import hana.lovepet.orderservice.api.domain.constant.OrderStatus
import hana.lovepet.orderservice.api.repository.OrderItemRepository
import hana.lovepet.orderservice.api.repository.OrderRepository
import hana.lovepet.orderservice.api.service.OrderService
import hana.lovepet.orderservice.common.clock.TimeProvider
import hana.lovepet.orderservice.common.exception.ApplicationException
import hana.lovepet.orderservice.common.exception.constant.ErrorCode.*
import hana.lovepet.orderservice.infrastructure.kafka.out.OrderEventPublisher
import hana.lovepet.orderservice.infrastructure.kafka.out.dto.OrderCreateEvent
import hana.lovepet.orderservice.infrastructure.kafka.out.dto.PaymentPrepareEvent
import hana.lovepet.orderservice.infrastructure.webClient.payment.PaymentServiceClient
import hana.lovepet.orderservice.infrastructure.webClient.payment.dto.request.ConfirmPaymentRequest
import hana.lovepet.orderservice.infrastructure.webClient.payment.dto.request.FailPaymentRequest
import hana.lovepet.orderservice.infrastructure.webClient.payment.dto.request.PaymentCancelRequest
import hana.lovepet.orderservice.infrastructure.webClient.payment.dto.request.PreparePaymentRequest
import hana.lovepet.orderservice.infrastructure.webClient.payment.dto.response.PreparePaymentResponse
import hana.lovepet.orderservice.infrastructure.webClient.product.ProductServiceClient
import hana.lovepet.orderservice.infrastructure.webClient.product.dto.request.ProductStockDecreaseRequest
import hana.lovepet.orderservice.infrastructure.webClient.product.dto.response.ProductInformationResponse
import hana.lovepet.orderservice.infrastructure.webClient.user.UserServiceClient
import hana.lovepet.orderservice.infrastructure.webClient.user.dto.UserExistResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class OrderServiceImpl(
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val timeProvider: TimeProvider,
    private val productServiceClient: ProductServiceClient,
    private val userServiceClient: UserServiceClient,
    private val paymentServiceClient: PaymentServiceClient,

//    private val orderEventPublisher: OrderEventPublisher,
    private val applicationEventPublisher: ApplicationEventPublisher,
) : OrderService {
    val log: Logger = LoggerFactory.getLogger(OrderServiceImpl::class.java)

    /**
     * 결제 준비
     */
    @Transactional
    override fun prepareOrder(createOrderRequest: CreateOrderRequest): PrepareOrderResponse {
        // 1. 유저 검증
        val user = validateUser(createOrderRequest)

        // 2. 주문 엔티티 생성(상태: PENDING_PAYMENT) + 저장
        val order = createAndSaveOrder(createOrderRequest, user.userName) // createdAt, orderNo 생성 등

        // 3. 상품 정보 조회 & OrderItem 스냅샷 생성
        val productIds = createOrderRequest.items.map { it.productId }
        val productInfos = productServiceClient.getProducts(productIds)
        validateProductInfo(createOrderRequest.items, productInfos)

        val orderItems = createOrderItems(order.id!!, createOrderRequest.items)

        // 4. 총액 계산 & 주문에 반영(아직 결제 전)
        val total = orderItems.sumOf { it.price * it.quantity }
        order.updateTotalPrice(total)

        // 5. 결제정보 저장
        applicationEventPublisher.publishEvent(
            PaymentPrepareEvent(
                eventId = UUID.randomUUID().toString(),
                occurredAt = timeProvider.now().toString(),
                orderId = order.id!!,
                userId = user.userId,
                amount = total,
                method = createOrderRequest.method,
                idempotencyKey = order.orderNo
            )
        )

        return PrepareOrderResponse(
            orderId = order.orderNo,
            amount = total
        )
    }

    @Transactional
    override fun mappedPaymentId(orderId: Long, paymentId:Long) {
        val order = orderRepository.findById(orderId).orElseThrow{ApplicationException(ORDER_NOT_FOUND, ORDER_NOT_FOUND.message)}
        if(order.paymentId == null) {
            order.mappedPaymentId(paymentId)
            orderRepository.save(order)
        }
        else {
            log.info("멱등처리. 이미 맵핑된 paymentId : order = $order")
        }
    }


    @Transactional(noRollbackFor = [ApplicationException::class])
    override fun confirmOrder(confirmOrderResponse: ConfirmOrderRequest): ConfirmOrderResponse {
        // 1. 주문 찾기 & 상태검증
        val order = orderRepository.findByOrderNo(confirmOrderResponse.orderId)
            ?: throw ApplicationException(ORDER_NOT_FOUND, ORDER_NOT_FOUND.message)

        // 이미 결제완료 되었으면 멱등 처리
        if (order.status == OrderStatus.CONFIRMED) {
            log.info("멱등처리. 이미 결제 완료된 주문 : order = $order")
            return ConfirmOrderResponse(
                success = true,
                orderNo = order.orderNo,
                paymentId = order.paymentId,
                message = "이미 결제 완료된 주문입니다."
            )
        }
        if (order.status != OrderStatus.CREATED) {
            return ConfirmOrderResponse(
                success = false,
                orderNo = order.orderNo,
                message = "결제가 가능한 상태가 아닙니다. [status=${order.status}]"
            )
        }

        if (order.price != confirmOrderResponse.amount) {
            log.error("결제 금액 불일치: expected=${order.price}, actual=${confirmOrderResponse.amount}, paymentConfirmResponse = $order")
            return ConfirmOrderResponse(
                success = false,
                orderNo = order.orderNo,
                message = "결제 금액 불일치: expected=${order.price}, actual=${confirmOrderResponse.amount}"
            )
        }

        // 3) 재고 차감 (부분 실패 시 여기서 예외 발생 가정)
        try {
            val orderItems = orderItemRepository.findAllByOrderId(order.id!!)
            decreaseStock(orderItems)
        } catch (e: ApplicationException) {
            log.error("재고차감실패! paymentId=${order.paymentId}, error=${e.message}")
            // 4) 보상 트랜잭션: 결제 취소 시도
            try {
                paymentServiceClient.cancel(
                    paymentId = order.paymentId!!,
                    paymentCancelRequest = PaymentCancelRequest(refundReason = "재고 차감 실패: ${e.message}")
                )
            } catch (cancelException: ApplicationException) {
                log.error("결제 취소 실패(수동 보상 필요) orderId=${order.id}, reason=${cancelException.getMessage}")
                throw ApplicationException(cancelException.errorCode, cancelException.getMessage)
            }

            order.fail(timeProvider)
            orderRepository.save(order)
            return ConfirmOrderResponse(
                success = false,
                orderNo = order.orderNo,
                message = "재고 차감 실패로 결제 취소됨"
            )
        }

        // 결제 확정
        try {
            paymentServiceClient.confirm(
                ConfirmPaymentRequest(
                    orderId = order.id!!,
                    orderNo = order.orderNo,
                    paymentId = order.paymentId!!,
                    paymentKey = confirmOrderResponse.paymentKey,
                    amount = confirmOrderResponse.amount,
                )
            )
        } catch (e: ApplicationException) {
            order.fail(timeProvider)
            orderRepository.save(order)
            log.error("paymentService 통신 중 오류발생 - paymentId=${order.paymentId}, error=${e.message}")
            return ConfirmOrderResponse(
                success = false,
                orderNo = order.orderNo,
                message = "결제서비스 통신 오류: ${e.message}"
            )
        }

        // 5) 최종 주문 확정
        order.confirm(timeProvider)
        orderRepository.save(order)

        return ConfirmOrderResponse(
            success = true,
            orderNo = order.orderNo,
            paymentId = order.paymentId,
            message = "결제/주문 확정 완료"
        )
    }

    @Transactional
    override fun failOrder(failOrderRequest: FailOrderRequest): Boolean {
        // 1. 주문 찾기 & 상태검증
        val order = orderRepository.findByOrderNo(failOrderRequest.orderId)
            ?: throw ApplicationException(ORDER_NOT_FOUND, ORDER_NOT_FOUND.message)

        // 2. 주문상태변경
        order.fail(timeProvider)

        paymentServiceClient.fail(
            FailPaymentRequest(
                orderId = order.id!!,
                paymentId = order.paymentId!!,
                message = failOrderRequest.message,
                code = failOrderRequest.code
            )
        )

        return true
    }

    override fun getOrders(
        orderSearchCondition: OrderSearchCondition,
        pageable: Pageable,
    ): Page<GetOrdersResponse> {
        return orderRepository.searchOrders(orderSearchCondition, pageable)
    }

    override fun getOrderItems(orderNo: String): List<GetOrderItemsResponse> {
        val foundOrder = orderRepository.findByOrderNo(orderNo) ?: throw ApplicationException(
            ORDER_NOT_FOUND,
            ORDER_NOT_FOUND.message
        )
        val orderItems = orderItemRepository.findAllByOrderId(foundOrder.id!!)


        val calculatedTotal = orderItems.sumOf { it.price * it.quantity }
        if (foundOrder.price != calculatedTotal) {
            log.warn("order의 총액과 orderItem 총액이 다릅니다.")
        }

        return orderItems.map {
            GetOrderItemsResponse(
                productId = it.productId,
                productName = it.productName,
                quantity = it.quantity,
                unitPrice = it.price,
                lineTotal = it.price * it.quantity
            )
        }
    }

    private fun createOrderItems(orderId: Long, items: List<CreateOrderItemRequest>): List<OrderItem> {
        val orderItems = items.map {
            OrderItem(
                productId = it.productId,
                productName = it.productName,
                quantity = it.quantity,
                price = it.price,
                orderId = orderId,
            )
        }

        orderItemRepository.saveAll(orderItems)
        return orderItems

    }

    private fun createAndSaveOrder(createOrderRequest: CreateOrderRequest, userName: String): Order {
        // 주문번호 생성
        val todayString = timeProvider.todayString()
        // TODO Redis 사용
        val maxOrderNo = orderRepository.findMaxOrderNoByToday("$todayString%")
        val nextSeq = if (maxOrderNo == null) 1 else maxOrderNo.substring(8).toInt() + 1
        val orderNo = todayString + "%07d".format(nextSeq)
//        val orderNo = UUID.randomUUID().toString().substring(10)
//        println("orderNo = ${orderNo}")

        val order = Order.create(createOrderRequest.userId, userName, orderNo, timeProvider)
        val savedOrder = orderRepository.save(order)
        return savedOrder
    }

    private fun validateUser(createOrderRequest: CreateOrderRequest): UserExistResponse {
        return userServiceClient.getUser(createOrderRequest.userId)
    }

    private fun validateProductInfo(
        createOrderItemRequests: List<CreateOrderItemRequest>,
        productsInfos: List<ProductInformationResponse>,
    ) {
        val requestedIds = createOrderItemRequests.map { it.productId }
        val foundIds = productsInfos.map { it.productId }
        val missing = requestedIds - foundIds
        if (missing.isNotEmpty()) {
            throw ApplicationException(PRODUCT_NOT_FOUND, "존재하지 않는 상품 productId: $missing")
        }

        createOrderItemRequests.forEach { req ->
            // .first -> 조건에 일치하는 첫번째 요소
            val stock = productsInfos.first { it.productId == req.productId }.stock
            if (stock < req.quantity) {
                throw ApplicationException(NOT_ENOUGH_STOCK, "재고 부족 productId: ${req.productId}")
            }
        }
    }


    private fun approvePayment(
        createOrderRequest: CreateOrderRequest,
        savedOrder: Order,
        totalPrice: Long,
    ): PreparePaymentResponse {

        return paymentServiceClient.approve(
            PreparePaymentRequest(
                userId = createOrderRequest.userId,
                orderId = savedOrder.id!!,
                amount = totalPrice,
                method = createOrderRequest.method
            )
        )
    }

    private fun decreaseStock(products: List<OrderItem>) {
        val productStockDecreaseRequests = products.map { ProductStockDecreaseRequest(it.productId, it.quantity) }
        productServiceClient.decreaseStock(productStockDecreaseRequests)
    }
}
