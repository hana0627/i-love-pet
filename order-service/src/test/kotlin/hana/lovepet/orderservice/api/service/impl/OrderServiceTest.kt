package hana.lovepet.orderservice.api.service.impl

import hana.lovepet.orderservice.api.controller.dto.request.OrderCreateRequest
import hana.lovepet.orderservice.api.controller.dto.request.OrderItemRequest
import hana.lovepet.orderservice.api.domain.Order
import hana.lovepet.orderservice.api.domain.constant.OrderStatus
import hana.lovepet.orderservice.api.repository.OrderItemRepository
import hana.lovepet.orderservice.api.repository.OrderRepository
import hana.lovepet.orderservice.api.service.OrderService
import hana.lovepet.orderservice.common.clock.TimeProvider
import hana.lovepet.orderservice.common.exception.ApplicationException
import hana.lovepet.orderservice.common.exception.constant.ErrorCode
import hana.lovepet.orderservice.infrastructure.webClient.payment.PaymentServiceClient
import hana.lovepet.orderservice.infrastructure.webClient.payment.dto.request.PaymentCreateRequest
import hana.lovepet.orderservice.infrastructure.webClient.payment.dto.response.PaymentCreateResponse
import hana.lovepet.orderservice.infrastructure.webClient.product.ProductServiceClient
import hana.lovepet.orderservice.infrastructure.webClient.product.dto.response.ProductInformationResponse
import hana.lovepet.orderservice.infrastructure.webClient.user.UserServiceClient
import hana.lovepet.orderservice.infrastructure.webClient.user.dto.UserExistResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.*
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class OrderServiceTest {
    @Mock
    lateinit var orderRepository: OrderRepository
    @Mock
    lateinit var orderItemRepository: OrderItemRepository
//    @SpyBean
    @Mock
    lateinit var timeProvider: TimeProvider
    @Mock
    lateinit var productServiceClient: ProductServiceClient
    @Mock
    lateinit var userServiceClient: UserServiceClient
    @Mock
    lateinit var paymentServiceClient: PaymentServiceClient
    lateinit var orderService: OrderService

    @BeforeEach
    fun setUp() {

//        val orderStatusManager = spy(OrderStatusManager(orderRepository, timeProvider))

        orderService = OrderServiceImpl(
            orderRepository,
            orderItemRepository,
            timeProvider,
            productServiceClient,
            userServiceClient,
            paymentServiceClient,
        )
    }

    @Test
    fun `상품주문에 성공한다`() {
        //given
        val userId = 1L
        val items = getItems()
        val method = "카드"
        given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 7, 10, 9, 0, 0))
        val todayString = "20250710"
        given(timeProvider.todayString()).willReturn(todayString)
        given(orderRepository.findMaxOrderNoByToday("${todayString}%")).willReturn("${todayString}00000001")

        val orderCreateRequest = OrderCreateRequest(userId, method, items)
        val order = Order.create(orderCreateRequest.userId,"${todayString}00000002", timeProvider)

        given(userServiceClient.getUser(userId)).willReturn(UserExistResponse(true))
        given(orderRepository.save(any())).willReturn(order.apply { id = 1L })


        val productsInfo = getProductsInfo(items)
        val ids = orderCreateRequest.items.map { it.productId }
        given(productServiceClient.getProducts(ids)).willReturn(productsInfo)
        val totalPrice = items.sumOf { it.price * it.quantity }

        val paymentCreateRequest = PaymentCreateRequest(userId, order.id!!, totalPrice, method)
        val paymentCreateResponse = PaymentCreateResponse(1000L, "success-payment-key", true)
        given(paymentServiceClient.approve(paymentCreateRequest)).willReturn(paymentCreateResponse)

        //when
        val result = orderService.createOrder(orderCreateRequest)

        //then
        then(userServiceClient).should().getUser(userId)
        then(orderRepository).should().findMaxOrderNoByToday("${todayString}%")
        then(orderRepository).should(times(2)).save(any())
//        then(orderRepository).should(times(3)).save(any())
        then(productServiceClient).should().getProducts(ids)
        then(paymentServiceClient).should().approve(paymentCreateRequest)
        then(orderItemRepository).should().saveAll(listOf(any()))

        assertThat(result.orderId).isEqualTo(order.id)
        assertThat(order.status).isEqualTo(OrderStatus.CONFIRMED)
        assertThat(order.userId).isEqualTo(userId)
        assertThat(order.createdAt).isEqualTo(timeProvider.now())
        assertThat(order.price).isEqualTo(totalPrice)
    }
    @Test
    fun `상품주문에 성공한다 하루 최초주문`() {
        //given
        val userId = 1L
        val items = getItems()
        val method = "카드"
        given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 7, 10, 9, 0, 0))
        val todayString = "20250710"
        given(timeProvider.todayString()).willReturn(todayString)
        given(orderRepository.findMaxOrderNoByToday("${todayString}%")).willReturn(null)

        val orderCreateRequest = OrderCreateRequest(userId, method, items)
        val order = Order.create(orderCreateRequest.userId,"${todayString}00000001", timeProvider)

        given(userServiceClient.getUser(userId)).willReturn(UserExistResponse(true))
        given(orderRepository.save(any())).willReturn(order.apply { id = 1L })


        val productsInfo = getProductsInfo(items)
        val ids = orderCreateRequest.items.map { it.productId }
        given(productServiceClient.getProducts(ids)).willReturn(productsInfo)
        val totalPrice = items.sumOf { it.price * it.quantity }

        val paymentCreateRequest = PaymentCreateRequest(userId, order.id!!, totalPrice, method)
        val paymentCreateResponse = PaymentCreateResponse(1000L, "success-payment-key", true)
        given(paymentServiceClient.approve(paymentCreateRequest)).willReturn(paymentCreateResponse)

        //when
        val result = orderService.createOrder(orderCreateRequest)

        //then
        then(userServiceClient).should().getUser(userId)
        then(orderRepository).should().findMaxOrderNoByToday("${todayString}%")
        then(orderRepository).should(times(2)).save(any())
//        then(orderRepository).should(times(3)).save(any())
        then(productServiceClient).should().getProducts(ids)
        then(paymentServiceClient).should().approve(paymentCreateRequest)
        then(orderItemRepository).should().saveAll(listOf(any()))

        assertThat(result.orderId).isEqualTo(order.id)
        assertThat(order.status).isEqualTo(OrderStatus.CONFIRMED)
        assertThat(order.userId).isEqualTo(userId)
        assertThat(order.createdAt).isEqualTo(timeProvider.now())
        assertThat(order.price).isEqualTo(totalPrice)
    }


    @Test
    fun `존재하지 않는 사용자가 상품 주문시 예외가 발생한다`() {
        //given
        val userId = 9999L
        val items = getItems()
        val method = "카드"
        val orderCreateRequest = OrderCreateRequest(userId, method, items)

        given(userServiceClient.getUser(userId)).willThrow(RuntimeException("error occurred while retrieving user exists [id : $userId]"))

        //when
        val result = assertThrows<RuntimeException> { orderService.createOrder(orderCreateRequest) }

        //then
        then(userServiceClient).should().getUser(userId)
        assertThat(result.message).isEqualTo("error occurred while retrieving user exists [id : $userId]")
    }

    @Test
    fun `상품정보 조회에 실패하면 예외가 발생한다`() {
        //given
        given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 7, 10, 9, 0, 0))
        val userId = 1L
        val items = getItems()
        val method = "카드"
        val orderCreateRequest = OrderCreateRequest(userId, method, items)

        val order = Order.create(orderCreateRequest.userId, "2025071000000001", timeProvider)

        given(userServiceClient.getUser(userId)).willReturn(UserExistResponse(true))
        given(orderRepository.save(any())).willReturn(order.apply { id = 1L })

        val ids = orderCreateRequest.items.map { it.productId }
        given(productServiceClient.getProducts(ids)).willThrow(RuntimeException("존재하지 않는 상품 ID: [1, 2]"))

        //when
        val result = assertThrows<RuntimeException> { orderService.createOrder(orderCreateRequest) }

        //then
        then(userServiceClient).should().getUser(userId)
        then(orderRepository).should().save(any())
//        then(orderRepository).should(times(2)).save(any())
        then(productServiceClient).should().getProducts(ids)

        assertThat(result.message).isEqualTo("존재하지 않는 상품 ID: [1, 2]")
    }

    @Test
    fun `없는 상품에 대한 조회 요청시 예외가 발생한다`() {
        //given
        given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 7, 10, 9, 0, 0))
        val userId = 1L
        val items = getItems()
        val method = "카드"
        val orderCreateRequest = OrderCreateRequest(userId, method, items)
        val order = Order.create(orderCreateRequest.userId, "2025071000000001", timeProvider)

        given(userServiceClient.getUser(userId)).willReturn(UserExistResponse(true))
        given(orderRepository.save(any())).willReturn(order.apply { id = 1L })

        val ids = orderCreateRequest.items.map { it.productId }
        val productsInfos = getProductsInfo(items)
        given(productServiceClient.getProducts(ids)).willReturn(listOf(
            productsInfos[0],
            productsInfos[1],
            productsInfos[2],
        ))

        //when
        val result = assertThrows<RuntimeException> { orderService.createOrder(orderCreateRequest) }

        //then
        then(userServiceClient).should().getUser(userId)
//        then(orderRepository).should().save(any())
        then(orderRepository).should(times(2)).save(any())
        then(productServiceClient).should().getProducts(ids)

        assertThat(order.status).isEqualTo(OrderStatus.FAIL)
        assertThat(result.message).isEqualTo("존재하지 않는 상품 productId: [${productsInfos[3].productId}, ${productsInfos[4].productId}]")
    }

    @Test
    fun `재고를 초과한 주문의 경우 예외가 발생한다`() {
        //given
        given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 7, 10, 9, 0, 0))
        val userId = 1L
        val method = "카드"
        val items = getItemsWithTooManyQuantity()
        val orderCreateRequest = OrderCreateRequest(userId, method, items)
        val order = Order.create(orderCreateRequest.userId, "2025071000000001", timeProvider)

        given(userServiceClient.getUser(userId)).willReturn(UserExistResponse(true))
        given(orderRepository.save(any())).willReturn(order.apply { id = 1L })

        val ids = orderCreateRequest.items.map { it.productId }
        val productsInfos = getProductsInfo(items)
        given(productServiceClient.getProducts(ids)).willReturn(productsInfos)

        //when
        val result = assertThrows<RuntimeException> { orderService.createOrder(orderCreateRequest) }

        //then
        then(userServiceClient).should().getUser(userId)
        then(orderRepository).should(times(2)).save(any())
//        then(orderRepository).should().save(any())
        then(productServiceClient).should().getProducts(ids)

        assertThat(order.status).isEqualTo(OrderStatus.FAIL)
        assertThat(result.message).isEqualTo("재고 부족 productId: ${productsInfos[0].productId}")

    }
    
    @Test
    fun `상품 주문중 통신오류 등의 이유로 예외가 발생할 수 있다`() {
        //given
        val userId = 1L
        val items = getItems()
        val method = "카드"
        given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 7, 10, 9, 0, 0))
        val todayString = "20250710"
        given(timeProvider.todayString()).willReturn(todayString)
        given(orderRepository.findMaxOrderNoByToday("${todayString}%")).willReturn("${todayString}00000001")

        val orderCreateRequest = OrderCreateRequest(userId, method, items)
        val order = Order.create(orderCreateRequest.userId,"${todayString}00000002", timeProvider)

        given(userServiceClient.getUser(userId)).willReturn(UserExistResponse(true))
        given(orderRepository.save(any())).willReturn(order.apply { id = 1L })


        val productsInfo = getProductsInfo(items)
        val ids = orderCreateRequest.items.map { it.productId }
        given(productServiceClient.getProducts(ids)).willReturn(productsInfo)
        val totalPrice = items.sumOf { it.price * it.quantity }

        val paymentCreateRequest = PaymentCreateRequest(userId, order.id!!, totalPrice, method)
        given(paymentServiceClient.approve(paymentCreateRequest)).willThrow(ApplicationException(ErrorCode.PAYMENTS_FAIL, "주문 결제 중 오류 발생: PG 통신 실패"))

        //when
        val result = assertThrows<ApplicationException> {orderService.createOrder(orderCreateRequest)}

        //then
        assertThat(order.status).isEqualTo(OrderStatus.FAIL)
        assertThat(result.message).isEqualTo("주문 결제 중 오류 발생: PG 통신 실패")
    }

    @Test
    fun `상품 주문 중 잔액 부족등의 이유로 예외가 발생할 수 있다`() {
        //given
        val userId = 1L
        val items = getItems()
        val method = "카드"
        given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 7, 10, 9, 0, 0))
        val todayString = "20250710"
        given(timeProvider.todayString()).willReturn(todayString)
        given(orderRepository.findMaxOrderNoByToday("${todayString}%")).willReturn("${todayString}00000001")

        val orderCreateRequest = OrderCreateRequest(userId, method, items)
        val order = Order.create(orderCreateRequest.userId,"${todayString}00000002", timeProvider)

        given(userServiceClient.getUser(userId)).willReturn(UserExistResponse(true))
        given(orderRepository.save(any())).willReturn(order.apply { id = 1L })


        val productsInfo = getProductsInfo(items)
        val ids = orderCreateRequest.items.map { it.productId }
        given(productServiceClient.getProducts(ids)).willReturn(productsInfo)
        val totalPrice = items.sumOf { it.price * it.quantity }

        val paymentCreateRequest = PaymentCreateRequest(userId, order.id!!, totalPrice, method)
        val paymentCreateResponse = PaymentCreateResponse(1000L, "success-payment-key", false, "잔액 부족")
        given(paymentServiceClient.approve(paymentCreateRequest)).willReturn(paymentCreateResponse)

        //when
        val result = assertThrows<RuntimeException> {orderService.createOrder(orderCreateRequest)}

        //then
        assertThat(order.status).isEqualTo(OrderStatus.FAIL)
        assertThat(result.message).isEqualTo("결제 실패: ${paymentCreateResponse.failReason}")
    }


    private fun getItems(): List<OrderItemRequest> {
        return listOf(
            OrderItemRequest(1L, 30000L, 1),
            OrderItemRequest(2L, 35000L, 1),
            OrderItemRequest(3L, 40000L, 5),
            OrderItemRequest(4L, 55000L, 7),
            OrderItemRequest(5L, 30000L, 10)
        )
    }


    private fun getItemsWithTooManyQuantity(): List<OrderItemRequest> {
        return listOf(
            OrderItemRequest(1L, 30000L, 9999),
            OrderItemRequest(2L, 35000L, 1),
            OrderItemRequest(3L, 40000L, 5),
            OrderItemRequest(4L, 55000L, 7),
            OrderItemRequest(5L, 30000L, 10)
        )
    }



    private fun getProductsInfo(items: List<OrderItemRequest>): List<ProductInformationResponse> {
        return listOf(
            ProductInformationResponse(productId = items[0].productId, name = "로얄캐닌 고양이 사료", price = 30000L, 1000),
            ProductInformationResponse(productId = items[1].productId, name = "로얄캐닌 고양이 사료 키튼", price = 35000L, 1000),
            ProductInformationResponse(productId = items[2].productId, name = "로얄캐닌 고양이 사료 인도어", price = 40000L, 1000),
            ProductInformationResponse(productId = items[3].productId, name = "가수분해 강아지 사료" , price = 55000L, 1000),
            ProductInformationResponse(productId = items[4].productId, name = "고단백 강아지 사료", price = 30000L, 1000),
        )
    }

}
