package hana.lovepet.orderservice.api.service.impl

import hana.lovepet.orderservice.api.controller.dto.request.CreateOrderRequest
import hana.lovepet.orderservice.api.controller.dto.request.CreateOrderItemRequest
import hana.lovepet.orderservice.api.controller.dto.request.OrderSearchCondition
import hana.lovepet.orderservice.api.controller.dto.response.GetOrderItemsResponse
import hana.lovepet.orderservice.api.controller.dto.response.GetOrdersResponse
import hana.lovepet.orderservice.api.controller.dto.response.OrderCreateResponse
import hana.lovepet.orderservice.api.domain.Order
import hana.lovepet.orderservice.api.domain.OrderItem
import hana.lovepet.orderservice.api.repository.OrderItemRepository
import hana.lovepet.orderservice.api.repository.OrderRepository
import hana.lovepet.orderservice.api.service.OrderService
import hana.lovepet.orderservice.common.clock.TimeProvider
import hana.lovepet.orderservice.common.exception.ApplicationException
import hana.lovepet.orderservice.common.exception.constant.ErrorCode.*
import hana.lovepet.orderservice.infrastructure.webClient.payment.PaymentServiceClient
import hana.lovepet.orderservice.infrastructure.webClient.payment.dto.request.PaymentCancelRequest
import hana.lovepet.orderservice.infrastructure.webClient.payment.dto.request.PaymentCreateRequest
import hana.lovepet.orderservice.infrastructure.webClient.payment.dto.response.PaymentCreateResponse
import hana.lovepet.orderservice.infrastructure.webClient.product.ProductServiceClient
import hana.lovepet.orderservice.infrastructure.webClient.product.dto.request.ProductStockDecreaseRequest
import hana.lovepet.orderservice.infrastructure.webClient.product.dto.response.ProductInformationResponse
import hana.lovepet.orderservice.infrastructure.webClient.user.UserServiceClient
import hana.lovepet.orderservice.infrastructure.webClient.user.dto.UserExistResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class OrderServiceImpl(
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val timeProvider: TimeProvider,
    private val productServiceClient: ProductServiceClient,
    private val userServiceClient: UserServiceClient,
    private val paymentServiceClient: PaymentServiceClient,
) : OrderService {
    val log: Logger = LoggerFactory.getLogger(OrderServiceImpl::class.java)

    /**
     * 1. 상품 정보는 먼저 조회하여 재고 및 가격 등의 유효성만 검증
     * 2. 결제가 최종 승인된 이후에만 상품 재고를 차감
     *
     * "비가역 작업(재고 차감)은 가장 마지막에 배치"하여
     *    - 결제 실패 시 불필요한 보상 트랜잭션을 방지하고
     *    - 장애 복구 및 상태 정합성을 유지할 수 있도록 설계함
     *
     * 재고 차감이 앞단에 위치하면 결제 실패 시 롤백이 어려워지고,
     *     product-service와의 불필요한 coupling 또는 보상 트랜잭션 설계가 필요해짐
     */
    @Transactional(noRollbackFor = [ApplicationException::class])
    override fun createOrder(createOrderRequest: CreateOrderRequest): OrderCreateResponse {

        // STEP1. 유저 검증
        val user = validateUser(createOrderRequest)

        // STEP2. Order 엔티티 생성 및 저장
        val savedOrder = createAndSaveOrder(createOrderRequest, user.userName)

        try {
            // STEP3. 상품 정보 조회
            val productIds: List<Long> = createOrderRequest.items.map { it.productId }
            val productsInfos: List<ProductInformationResponse> = productServiceClient.getProducts(productIds)

            // STEP4. OrderItem 엔티티 생성
            val orderItems = createOrderItems(savedOrder.id!!, createOrderRequest.items)

            // STEP5. 상품정보검증
            validateProductInfo(createOrderRequest.items, productsInfos)

            // STEP6. 총 결제금액 계산
            val totalPrice = orderItems.sumOf { it.price * it.quantity }

            // STEP7. Order 엔티티에 총 주문금액 저장
            savedOrder.updateTotalPrice(totalPrice)

            // STEP8. 페이먼트 연동
            try {
                val response = approvePayment(createOrderRequest, savedOrder, totalPrice)

                savedOrder.mappedPaymentId(response.paymentId)
                if (!response.isSuccess) {
                    throw ApplicationException(PAYMENTS_FAIL, response.failReason ?: "결제가 승인되지 않았습니다.")
                }
            } catch (e: ApplicationException) {
                // 결제 실패
                log.info("결제 실패 orderId : {}, error : {}", savedOrder.id, e.getMessage)
                throw ApplicationException(PAYMENTS_FAIL, e.getMessage)
            }

            // STEP9. 재고감소
            try {
                decreaseStock(createOrderRequest.items)
            } catch (e: ApplicationException) {
                // 재고차감 실패
                log.info("재고 차감 실패 : ${e.getMessage}")
                try {
                    paymentServiceClient.cancel(paymentId = savedOrder.paymentId, PaymentCancelRequest(refundReason = "상품 재고 차감 실패"))
                } catch (e: ApplicationException) {
                    log.error("결제 취소 실패(수동 보상 필요) orderId=${savedOrder.id}, reason=${e.getMessage}")
                    throw ApplicationException(e.errorCode, e.getMessage)
                }
                throw ApplicationException(STOCK_DECREASE_FAIL, e.getMessage)
            }

            // STEP10. 주문 확정
            savedOrder.confirm(timeProvider)

        } catch (e: ApplicationException) {
            savedOrder.fail(timeProvider)
            orderRepository.save(savedOrder)
            throw ApplicationException(e.errorCode, e.getMessage)
        }

        orderRepository.save(savedOrder)

        return OrderCreateResponse(savedOrder.id!!)
    }

    override fun getOrders(
        orderSearchCondition: OrderSearchCondition,
        pageable: Pageable,
    ): Page<GetOrdersResponse> {
        return orderRepository.searchOrders(orderSearchCondition, pageable)
    }

    override fun getOrderItems(orderNo: String): List<GetOrderItemsResponse> {
        val foundOrder = orderRepository.findByOrderNo(orderNo) ?: throw ApplicationException(ORDER_NOT_FOUND, ORDER_NOT_FOUND.message)
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
    ): PaymentCreateResponse{

        return paymentServiceClient.approve(PaymentCreateRequest(
            userId = createOrderRequest.userId,
            orderId = savedOrder.id!!,
            amount = totalPrice,
            method = createOrderRequest.method
        ))
//        val paymentRequest = PaymentCreateRequest(
//            userId = createOrderRequest.userId,
//            orderId = savedOrder.id!!,
//            amount = totalPrice,
//            method = createOrderRequest.method
//        )
//
//        val paymentResponse = paymentServiceClient.approve(paymentRequest)
//        if (paymentResponse.isSuccess) {
//            return paymentResponse
//        } else {
//            throw ApplicationException(PAYMENTS_FAIL, "결제 실패: ${paymentResponse.failReason}")
//        }
    }

    private fun decreaseStock(products: List<CreateOrderItemRequest>) {
        val productStockDecreaseRequests = products.map { ProductStockDecreaseRequest(it.productId, it.quantity) }
        productServiceClient.decreaseStock(productStockDecreaseRequests)
    }

//    private fun cancelOrder(savedOrder: Order) {
//        savedOrder.cancel(timeProvider)
//        orderRepository.save(savedOrder)
//    }

}
