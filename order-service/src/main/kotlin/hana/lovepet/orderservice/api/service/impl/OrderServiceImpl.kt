package hana.lovepet.orderservice.api.service.impl

import hana.lovepet.orderservice.api.controller.dto.request.CreateOrderRequest
import hana.lovepet.orderservice.api.controller.dto.request.CreateOrderItemRequest
import hana.lovepet.orderservice.api.controller.dto.request.OrderSearchCondition
import hana.lovepet.orderservice.api.controller.dto.request.ConfirmOrderRequest
import hana.lovepet.orderservice.api.controller.dto.response.GetOrderItemsResponse
import hana.lovepet.orderservice.api.controller.dto.response.GetOrdersResponse
import hana.lovepet.orderservice.api.controller.dto.response.ConfirmOrderResponse
import hana.lovepet.orderservice.api.controller.dto.response.OrderStatusResponse
import hana.lovepet.orderservice.api.controller.dto.response.PrepareOrderResponse
import hana.lovepet.orderservice.api.domain.Order
import hana.lovepet.orderservice.api.domain.OrderItem
import hana.lovepet.orderservice.api.domain.constant.OrderStatus
import hana.lovepet.orderservice.api.repository.OrderCacheRepository
import hana.lovepet.orderservice.api.repository.OrderItemRepository
import hana.lovepet.orderservice.api.repository.OrderRepository
import hana.lovepet.orderservice.api.service.OrderService
import hana.lovepet.orderservice.common.clock.TimeProvider
import hana.lovepet.orderservice.common.exception.ApplicationException
import hana.lovepet.orderservice.common.exception.constant.ErrorCode.*
import hana.lovepet.orderservice.infrastructure.kafka.`in`.dto.ProductsInformationResponseEvent.ProductInformationResponse
import hana.lovepet.orderservice.infrastructure.kafka.out.OrderEventPublisher
import hana.lovepet.orderservice.infrastructure.kafka.out.dto.GetProductsEvent
import hana.lovepet.orderservice.infrastructure.kafka.out.dto.GetProductsEvent.OrderItemRequest
import hana.lovepet.orderservice.infrastructure.kafka.out.dto.PaymentCancelEvent
import hana.lovepet.orderservice.infrastructure.kafka.out.dto.PaymentPendingEvent
import hana.lovepet.orderservice.infrastructure.kafka.out.dto.PaymentPrepareEvent
import hana.lovepet.orderservice.infrastructure.webClient.payment.PaymentServiceClient
import hana.lovepet.orderservice.infrastructure.kafka.out.dto.ProductStockDecreaseEvent
import hana.lovepet.orderservice.infrastructure.kafka.out.dto.ProductStockRollbackEvent
//import hana.lovepet.orderservice.infrastructure.webClient.product.dto.response.ProductInformationResponse
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
//    private val productServiceClient: ProductServiceClient,
    private val userServiceClient: UserServiceClient,
    private val paymentServiceClient: PaymentServiceClient,

    private val orderEventPublisher: OrderEventPublisher,
    private val applicationEventPublisher: ApplicationEventPublisher,

    private val orderCacheRepository: OrderCacheRepository,
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
        // ** OrderStatus - CREATED **
        val order = createAndSaveOrder(createOrderRequest, user.userName) // createdAt, orderNo 생성 등

        // 3. 상품 정보 조회 & OrderItem 스냅샷 생성
        val getProductEventId = UUID.randomUUID().toString()

        applicationEventPublisher.publishEvent(
            GetProductsEvent(
                eventId = getProductEventId,
                orderId = order.id!!,
                items = createOrderRequest.items.map {
                    OrderItemRequest(
                        productId = it.productId,
                        quantity = it.quantity
                    )
                },
                idempotencyKey = order.orderNo
            )
        )

        // ** OrderStatus - VALIDATING **
        order.updateStatus(OrderStatus.VALIDATING, timeProvider)


        createAndSaveOrderItems(order.id!!, createOrderRequest.items)

        // eventId를 통해 계속 polling할 예정
        return PrepareOrderResponse(
            orderId = order.orderNo,
            eventId = getProductEventId,
            amount = null,
            status = order.status
        )
    }

    @Transactional
    override fun mappedTotalAmount(orderId: Long, products: List<ProductInformationResponse>) {

        val order = orderRepository.findById(orderId)
            .orElseThrow { ApplicationException(ORDER_NOT_FOUND, ORDER_NOT_FOUND.message) }

        if (order.price != 0L) {
            log.warn("이미 총액이 계산된 주문입니다. orderId=$orderId")
            return
        }

        val orderItems = orderItemRepository.findAllByOrderId(order.id!!)

        var total = 0L

        try {
            // 순수 검증 로직만 try-catch 안에
            validateProductInfo(orderItems, products)
            updateOrderItemPrices(orderItems, products)
            total = orderItems.sumOf { it.price * it.quantity }
            order.updateTotalPrice(total)

        } catch (e: Exception) {
            // 검증 실패 시에만 VALIDATION_FAILED
            order.updateStatus(OrderStatus.VALIDATION_FAILED, timeProvider)
            // TODO 메서드로 변경
            order.description = e.message
            orderRepository.save(order)
            log.error("상품 검증 실패. orderId=$orderId, error=${e.message}", e)
            return
        }

        order.updateStatus(OrderStatus.VALIDATION_SUCCESS, timeProvider)
        orderRepository.save(order)
        orderItemRepository.saveAll(orderItems)

        applicationEventPublisher.publishEvent(
            PaymentPrepareEvent(
                eventId = UUID.randomUUID().toString(),
                occurredAt = timeProvider.now().toString(),
                orderId = order.id!!,
                userId = order.userId,
                amount = total,
                method = order.paymentMethod,
                idempotencyKey = order.orderNo
            )
        )
    }

    @Transactional
    override fun validationFail(orderId: Long) {
        val order = orderRepository.findById(orderId)
            .orElseThrow { ApplicationException(ORDER_NOT_FOUND, ORDER_NOT_FOUND.message) }
        order.updateStatus(status = OrderStatus.VALIDATION_FAILED, timeProvider)
        orderRepository.save(order)
    }

    @Transactional
    override fun orderProcessFail(orderId: Long) {
        val order = orderRepository.findById(orderId)
            .orElseThrow { ApplicationException(ORDER_NOT_FOUND, ORDER_NOT_FOUND.message) }
        order.updateStatus(status = OrderStatus.PROCESSING_FAILED, timeProvider)
        orderRepository.save(order)
    }

    @Transactional
    override fun paymentPrepareFail(orderId: Long) {
        val order = orderRepository.findById(orderId)
            .orElseThrow { ApplicationException(ORDER_NOT_FOUND, ORDER_NOT_FOUND.message) }
        order.updateStatus(status = OrderStatus.PAYMENT_PREPARE_FAIL, timeProvider)
        orderRepository.save(order)
    }


    @Transactional
    override fun mappedPaymentId(orderId: Long, paymentId: Long) {
        val order = orderRepository.findById(orderId)
            .orElseThrow { ApplicationException(ORDER_NOT_FOUND, ORDER_NOT_FOUND.message) }
        if (order.paymentId == null) {
            order.mappedPaymentId(paymentId)
            order.updateStatus(status = OrderStatus.PREPARED, timeProvider)
            orderRepository.save(order)
        } else {
            log.info("멱등처리. 이미 맵핑된 paymentId : order = $order")
        }
    }

    @Transactional(readOnly = true)
    override fun getStatus(orderNo: String): OrderStatusResponse {
        val order = orderRepository.findByOrderNo(orderNo)
            ?: throw ApplicationException(ORDER_NOT_FOUND, ORDER_NOT_FOUND.message)

        return when (order.status) {
            OrderStatus.PREPARED -> OrderStatusResponse(
                orderNo = order.orderNo,
                status = order.status,
                amount = order.price,
                errorMessage = null,
            )

            OrderStatus.CONFIRMED -> OrderStatusResponse(
                orderNo = order.orderNo,
                status = order.status,
                amount = order.price,
                errorMessage = null,
            )

            else -> OrderStatusResponse(
                orderNo = order.orderNo,
                status = order.status,
                amount = null,
                errorMessage = order.description
            )
        }
    }

    /**
     * 결제 요청
     */
    @Transactional
    override fun OrderItemDecreaseRequest(confirmOrderResponse: ConfirmOrderRequest): ConfirmOrderResponse {
        // 1. 주문 찾기 & 상태검증
        val order = orderRepository.findByOrderNo(confirmOrderResponse.orderId)
            ?: throw ApplicationException(ORDER_NOT_FOUND, ORDER_NOT_FOUND.message)

        // 이미 결제완료 되었으면 멱등 처리
        if (order.status == OrderStatus.DECREASE_STOCK) {
            log.info("멱등처리. 이미 결제 완료된 주문 : order = $order")
            return ConfirmOrderResponse(
                success = true,
                orderNo = order.orderNo,
//                paymentId = order.paymentId,
                message = "이미 결제 완료된 주문입니다."
            )
        }
        if (order.status == OrderStatus.CONFIRMED) {
            log.info("멱등처리. 이미 결제 완료된 주문 : order = $order")
            return ConfirmOrderResponse(
                success = true,
                orderNo = order.orderNo,
//                paymentId = order.paymentId,
                message = "이미 결제 완료된 주문입니다."
            )
        }
        if (order.status != OrderStatus.PREPARED) {
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

        val orderItems = orderItemRepository.findAllByOrderId(order.id!!)


        orderCacheRepository.setPaymentKey(order.id!!, confirmOrderResponse.paymentKey)

        decreaseStock(order.id!!, order.orderNo, orderItems)

        order.updateStatus(OrderStatus.DECREASE_STOCK, timeProvider)
        orderRepository.save(order)

        return ConfirmOrderResponse(
            success = true,
            orderNo = order.orderNo,
//            paymentId = order.paymentId,
//            message = "결제/주문 확정 완료"
        )
    }


    @Transactional
    override fun processOrder(orderId: Long) {
        val order = orderRepository.findById(orderId)
            .orElseThrow { ApplicationException(ORDER_NOT_FOUND, ORDER_NOT_FOUND.message) }
        order.updateStatus(OrderStatus.PAYMENT_PENDING, timeProvider)

        val paymentKey = orderCacheRepository.findPaymentKeyByOrderId(order.id!!)
            ?: throw ApplicationException(PAYMENT_KEY_EXPIRED, "PaymentKey 만료 또는 누락")

        applicationEventPublisher.publishEvent(
            PaymentPendingEvent(
                eventId = UUID.randomUUID().toString(),
                paymentId = order.paymentId!!,
                orderId = orderId,
                orderNo = order.orderNo,
                paymentKey = paymentKey,
                amount = order.price,
                idempotencyKey = paymentKey
            )
        )
    }

    @Transactional
    override fun decreaseStockFail(orderId: Long) {
        val order = orderRepository.findById(orderId)
            .orElseThrow { ApplicationException(ORDER_NOT_FOUND, ORDER_NOT_FOUND.message) }
        order.updateStatus(OrderStatus.DECREASE_STOCK_FAIL, timeProvider)

        orderRepository.save(order)
//        applicationEventPublisher.publishEvent(
//            PaymentCancelEvent(
//                eventId = UUID.randomUUID().toString(),
//                orderId = order.id!!,
//                orderNo = order.orderNo,
//                paymentId = order.paymentId!!,
//                refundReason = "재고차감실패: orderId: $orderId",
//                idempotencyKey = order.orderNo,
//            )
//        )
    }

    @Transactional
    override fun rollbackStockAndCancel(orderId: Long) {
        // 1. 주문 찾기 & 상태검증
        val order = orderRepository.findById(orderId)
            .orElseThrow { ApplicationException(ORDER_NOT_FOUND, ORDER_NOT_FOUND.message) }

        val orderItems = orderItemRepository.findAllByOrderId(orderId)

        val productMap = orderItems.map { ProductStockRollbackEvent.Product(it.productId, it.quantity) }

        // 2. 주문상태변경
        order.fail(timeProvider)
        orderRepository.save(order)


        applicationEventPublisher.publishEvent(
            ProductStockRollbackEvent(
                eventId = UUID.randomUUID().toString(),
                orderId = orderId,
                products = productMap,
                idempotencyKey = order.orderNo
            )
        )
    }

    @Transactional
    override fun confirmedOrder(orderId: Long) {
        val order = orderRepository.findById(orderId)
            .orElseThrow { ApplicationException(ORDER_NOT_FOUND, ORDER_NOT_FOUND.message) }

        order.updateStatus(OrderStatus.CONFIRMED, timeProvider)

        orderRepository.save(order)

    }

    @Transactional
    override fun failOrder(orderId: Long): Boolean {
        // 1. 주문 찾기 & 상태검증
        val order = orderRepository.findById(orderId)
            .orElseThrow { ApplicationException(ORDER_NOT_FOUND, ORDER_NOT_FOUND.message) }
        val orderItems = orderItemRepository.findAllByOrderId(orderId)

        val productMap = orderItems.map { ProductStockRollbackEvent.Product(it.productId, it.quantity) }

        // 2. 주문상태변경
        order.updateStatus(OrderStatus.PAYMENT_FAILED, timeProvider)
        orderRepository.save(order)

        applicationEventPublisher.publishEvent(
            ProductStockRollbackEvent(
                eventId = UUID.randomUUID().toString(),
                orderId = order.id!!,
                products = productMap,
                idempotencyKey = order.orderNo,
            )
        )
        return true
    }

    @Transactional
    override fun canceledOrder(orderId: Long) {
        val order = orderRepository.findById(orderId)
            .orElseThrow { ApplicationException(ORDER_NOT_FOUND, ORDER_NOT_FOUND.message) }

        order.updateStatus(OrderStatus.CANCELED, timeProvider)

        orderRepository.save(order)
    }

    @Transactional
    override fun canceledFailOrder(orderId: Long) {
        val order = orderRepository.findById(orderId)
            .orElseThrow { ApplicationException(ORDER_NOT_FOUND, ORDER_NOT_FOUND.message) }

        order.updateStatus(OrderStatus.FAIL, timeProvider)
        order.updateDescription("결제 취소 실패 - 수동 개입 필요 (결제 상태 확인 후 처리)")

        orderRepository.save(order)
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

    private fun createAndSaveOrderItems(orderId: Long, items: List<CreateOrderItemRequest>): List<OrderItem> {
        val orderItems = items.map {
            OrderItem(
                productId = it.productId,
                productName = it.productName,
                quantity = it.quantity,
                price = 0,
                orderId = orderId,
            )
        }

        orderItemRepository.saveAll(orderItems)
        return orderItems

    }

    private fun createAndSaveOrder(createOrderRequest: CreateOrderRequest, userName: String): Order {
        // Redis를 이용한 주문번호 생성
        val todayString = timeProvider.todayString()
        val orderNo = orderCacheRepository.getNextOrderNumber(todayString)

        val order = Order.create(createOrderRequest.userId, userName, orderNo, createOrderRequest.method, timeProvider)
        val savedOrder = orderRepository.save(order)
        return savedOrder
    }

    private fun validateUser(createOrderRequest: CreateOrderRequest): UserExistResponse {
        return userServiceClient.getUser(createOrderRequest.userId)
    }

    private fun validateProductInfo(
        orderItems: List<OrderItem>,
        productInfos: List<ProductInformationResponse>,
    ) {
        val requestedMap = orderItems.associateBy { it.productId }
        val productMap = productInfos.associateBy { it.productId }

        // 존재하지 않는 상품 체크
        val missing = requestedMap.keys - productMap.keys
        if (missing.isNotEmpty()) {
            throw ApplicationException(PRODUCT_NOT_FOUND, "존재하지 않는 상품 productId: $missing")
        }

        // 재고 부족 체크
        requestedMap.forEach { (productId, orderItem) ->
            val product = productMap[productId]!!
            if (product.stock < orderItem.quantity) {
                throw ApplicationException(
                    NOT_ENOUGH_STOCK,
                    "재고 부족 productId: $productId, 요청수량: ${orderItem.quantity}, 재고: ${product.stock}"
                )
            }
        }
    }

    private fun updateOrderItemPrices(orderItems: List<OrderItem>, products: List<ProductInformationResponse>) {
        val productMap = products.associateBy { it.productId }

        orderItems.forEach { it ->
            val product = productMap[it.productId]!!
            it.updateProductNameAndPrice(product.productName, product.price)
        }
    }

    private fun decreaseStock(
        orderId: Long,
        orderNo: String,
        products: List<OrderItem>,
    ) {
        val productMap = products.map { ProductStockDecreaseEvent.Product(it.productId, it.quantity) }

        applicationEventPublisher.publishEvent(
            ProductStockDecreaseEvent(
                eventId = UUID.randomUUID().toString(),
                orderId = orderId,
                products = productMap,
                idempotencyKey = orderNo,
            )
        )

    }

}
