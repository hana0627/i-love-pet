package hana.lovepet.orderservice.api.service.impl

import hana.lovepet.orderservice.api.controller.dto.request.OrderCreateRequest
import hana.lovepet.orderservice.api.controller.dto.request.OrderItemRequest
import hana.lovepet.orderservice.api.controller.dto.response.OrderCreateResponse
import hana.lovepet.orderservice.api.domain.Order
import hana.lovepet.orderservice.api.domain.OrderItem
import hana.lovepet.orderservice.api.repository.OrderItemRepository
import hana.lovepet.orderservice.api.repository.OrderRepository
import hana.lovepet.orderservice.api.service.OrderService
import hana.lovepet.orderservice.common.clock.TimeProvider
import hana.lovepet.orderservice.infrastructure.webClient.product.ProductServiceClient
import hana.lovepet.orderservice.infrastructure.webClient.product.dto.ProductInformationResponse
import hana.lovepet.orderservice.infrastructure.webClient.user.UserServiceClient
import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class OrderServiceImpl (
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val productServiceClient: ProductServiceClient,
    private val userServiceClient: UserServiceClient,
    private val timeProvider: TimeProvider
) : OrderService{

    @Transactional
    override fun createOrder(orderCreateRequest: OrderCreateRequest): OrderCreateResponse {
        // STEP1. 유저 검증
        validateUser(orderCreateRequest)

        // STEP2. Order 엔티티 생성 및 저장
        val savedOrder = createAndSaveOrder(orderCreateRequest)

        // STEP3. 상품 정보 조회 (배치 API 사용)
        val productIds: List<Long> = orderCreateRequest.items.map { it.productId }
        val productsInfos: List<ProductInformationResponse> = productServiceClient.getProducts(productIds)
        // STEP4. 상품정보검증
        validateProductInfo(orderCreateRequest.items, productsInfos)

        // STEP5. OrderItem 엔티티 생성
        val orderItems = createOrderItems(savedOrder.id!!, orderCreateRequest.items)

        // STEP6. 총 결제금액 계산
        val totalPrice = orderItems.sumOf { it.price * it.quantity }

        // STEP7. Order 엔티티에 총 주문금액 저장
        savedOrder.updateTotalPrice(totalPrice)

        // STEP8. (미구현) 페이먼트 연동
        // → PaymentServiceClient.authorize(...)
        // → kafka 이벤트 발행 or 동기 응답 처리
        // 주문완료 처리
        savedOrder.confirm(timeProvider)

        orderRepository.save(savedOrder)

        return OrderCreateResponse(savedOrder.id!!)
    }

    private fun createOrderItems(orderId: Long, items: List<OrderItemRequest>): List<OrderItem> {
        val orderItems = items.map {
            OrderItem(
                productId = it.productId,
                quantity = it.quantity,
                price = it.price,
                orderId = orderId,
            )
        }

        orderItemRepository.saveAll(orderItems) // 부수 효과: 저장
        return orderItems

    }

    private fun createAndSaveOrder(orderCreateRequest: OrderCreateRequest): Order {
        // 주문번호 생성
        val todayString = timeProvider.todayString()
        val maxOrderNo = orderRepository.findMaxOrderNoByToday("$todayString%")
        val nextSeq = if (maxOrderNo == null) 1 else maxOrderNo.substring(8).toInt() + 1
        val orderNo = todayString + "%07d".format(nextSeq)



        val order = Order.create(orderCreateRequest.userId, orderNo, timeProvider)
        val savedOrder = orderRepository.save(order)
        return savedOrder
    }

    private fun validateUser(orderCreateRequest: OrderCreateRequest) {
        userServiceClient.getUser(orderCreateRequest.userId)
    }

    private fun validateProductInfo(orderItemRequests: List<OrderItemRequest>, productsInfos: List<ProductInformationResponse>) {
        val requestedIds = orderItemRequests.map { it.productId }
        val foundIds     = productsInfos.map { it.productId }
        val missing      = requestedIds - foundIds
        if (missing.isNotEmpty()) {
            throw EntityNotFoundException("존재하지 않는 상품 productId: $missing")
        }

        orderItemRequests.forEach { req ->
            // .first -> 조건에 일치하는 첫번째 요소
            val stock = productsInfos.first { it.productId == req.productId }.stock
            if (stock < req.quantity) {
                throw IllegalStateException("재고 부족 productId: ${req.productId}")
            }
        }
    }

}
