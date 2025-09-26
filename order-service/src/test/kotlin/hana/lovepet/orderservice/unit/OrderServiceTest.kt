package hana.lovepet.orderservice.unit

import hana.lovepet.orderservice.api.controller.dto.request.ConfirmOrderRequest
import hana.lovepet.orderservice.api.controller.dto.request.CreateOrderRequest
import hana.lovepet.orderservice.api.controller.dto.request.CreateOrderItemRequest
import hana.lovepet.orderservice.api.controller.dto.request.OrderSearchCondition
import hana.lovepet.orderservice.api.controller.dto.response.GetOrdersResponse
import hana.lovepet.orderservice.api.controller.dto.response.GetOrderItemsResponse
import hana.lovepet.orderservice.api.domain.Order
import hana.lovepet.orderservice.api.domain.OrderItem
import hana.lovepet.orderservice.api.domain.constant.OrderStatus
import hana.lovepet.orderservice.api.repository.OrderCacheRepository
import hana.lovepet.orderservice.api.repository.OrderItemRepository
import hana.lovepet.orderservice.api.repository.OrderRepository
import hana.lovepet.orderservice.api.service.OrderService
import hana.lovepet.orderservice.api.service.impl.OrderServiceImpl
import hana.lovepet.orderservice.common.clock.TimeProvider
import hana.lovepet.orderservice.infrastructure.kafka.out.OrderEventPublisher
import hana.lovepet.orderservice.infrastructure.kafka.out.dto.PaymentPrepareEvent
import hana.lovepet.orderservice.infrastructure.kafka.out.dto.PaymentPendingEvent
import hana.lovepet.orderservice.infrastructure.kafka.`in`.dto.ProductsInformationResponseEvent.ProductInformationResponse
import hana.lovepet.orderservice.infrastructure.webClient.payment.PaymentServiceClient
import hana.lovepet.orderservice.infrastructure.webClient.user.UserServiceClient
import hana.lovepet.orderservice.infrastructure.webClient.user.dto.UserExistResponse
import hana.lovepet.orderservice.common.exception.ApplicationException
import hana.lovepet.orderservice.common.exception.constant.ErrorCode
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.then
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockitoExtension::class)
class OrderServiceTest {

    @Mock
    lateinit var orderRepository: OrderRepository

    @Mock
    lateinit var orderItemRepository: OrderItemRepository
    @Mock
    lateinit var timeProvider: TimeProvider

    @Mock
    lateinit var userServiceClient: UserServiceClient

    @Mock
    lateinit var paymentServiceClient: PaymentServiceClient

    @Mock
    lateinit var orderEventPublisher: OrderEventPublisher

    @Mock
    lateinit var applicationEventPublisher: ApplicationEventPublisher

    @Mock
    lateinit var orderCacheRepository: OrderCacheRepository

    lateinit var orderService: OrderService

    @BeforeEach
    fun setUp() {
        orderService = OrderServiceImpl(
            orderRepository = orderRepository,
            orderItemRepository = orderItemRepository,
            timeProvider = timeProvider,
            userServiceClient = userServiceClient,
            paymentServiceClient = paymentServiceClient,
            orderEventPublisher = orderEventPublisher,
            applicationEventPublisher = applicationEventPublisher,
            orderCacheRepository = orderCacheRepository
        )
    }

    @Test
    fun `주문 준비에 성공한다`() {
        //given
        val userId = 1000L
        val userName = "박하나"
        val paymentMethod = "카드"
        val todayString = "20250803"
        val orderNo = "2025080300000001"

        val createOrderRequest = CreateOrderRequest(
            userId = userId,
            method = paymentMethod,
            items = listOf(
                CreateOrderItemRequest(
                    productId = 1L,
                    productName = "로얄캐닌 강아지 사료",
                    price = 35000L,
                    quantity = 2
                ),
                CreateOrderItemRequest(
                    productId = 2L,
                    productName = "로얄캐닌 고양이 사료",
                    price = 40000L,
                    quantity = 1
                )
            )
        )

        given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 8, 3, 9, 0, 0))

        val savedOrder = Order.create(userId, userName, orderNo, paymentMethod, timeProvider).apply {
            ReflectionTestUtils.setField(this, "id", 1L)
        }

        given(userServiceClient.getUser(userId)).willReturn(
            UserExistResponse(
                userId = userId,
                userName = userName
            )
        )
        given(timeProvider.todayString()).willReturn(todayString)
//        given(orderRepository.findMaxOrderNoByToday("$todayString%")).willReturn(null)
        given(orderCacheRepository.getNextOrderNumber(todayString)).willReturn("${todayString}00000001")
        given(orderRepository.save(any<Order>())).willReturn(savedOrder)

        //when
        val result = orderService.prepareOrder(createOrderRequest)

        //then
        then(userServiceClient).should().getUser(userId)
        then(orderRepository).should().save(any<Order>())
        then(orderItemRepository).should().saveAll(any<List<OrderItem>>())

        assertThat(result.orderId).isEqualTo(orderNo)
        assertThat(result.eventId).isNotNull()
        assertThat(result.amount).isNull()
        assertThat(result.status).isEqualTo(OrderStatus.VALIDATING)
    }

    @Test
    fun `주문번호 생성시 기존 주문이 있으면 시퀀스가 증가한다`() {
        //given
        val userId = 1000L
        val userName = "박하나"
        val paymentMethod = "카드"
        val todayString = "20250803"
        val existingOrderNo = "2025080300000005"
        val expectedOrderNo = "2025080300000006"

        val createOrderRequest = CreateOrderRequest(
            userId = userId,
            method = paymentMethod,
            items = listOf(
                CreateOrderItemRequest(
                    productId = 1L,
                    productName = "로얄캐닌 강아지 사료",
                    price = 40000L,
                    quantity = 1
                )
            )
        )

        given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 8, 3, 9, 0, 0))

        val savedOrder = Order.create(userId, userName, expectedOrderNo, paymentMethod, timeProvider).apply {
            ReflectionTestUtils.setField(this, "id", 1L)
        }

        given(userServiceClient.getUser(userId)).willReturn(
            UserExistResponse(
                userId = userId,
                userName = userName
            )
        )

        given(timeProvider.todayString()).willReturn(todayString)
        given(orderCacheRepository.getNextOrderNumber(todayString)).willReturn(existingOrderNo)

        given(orderRepository.save(any<Order>())).willReturn(savedOrder)

        //when
        val result = orderService.prepareOrder(createOrderRequest)

        //then
        assertThat(result.orderId).isEqualTo(expectedOrderNo)
    }

    @Test
    fun `주문 생성시 상태가 CREATED에서 VALIDATING으로 변경된다`() {
        //given
        val userId = 1000L
        val userName = "박하나"
        val paymentMethod = "카드"
        val todayString = "20250803"
        val orderNo = "2025080300000001"

        val createOrderRequest = CreateOrderRequest(
            userId = userId,
            method = paymentMethod,
            items = listOf(
                CreateOrderItemRequest(
                    productId = 1L,
                    productName = "로얄캐닌 강아지 사료",
                    price = 40000L,
                    quantity = 1
                )
            )
        )

        given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 8, 3, 9, 0, 0))

        val savedOrder = Order.create(userId, userName, orderNo, paymentMethod, timeProvider).apply {
            ReflectionTestUtils.setField(this, "id", 1L)
        }

        given(userServiceClient.getUser(userId)).willReturn(
            UserExistResponse(
                userId = userId,
                userName = userName
            )
        )
        given(timeProvider.todayString()).willReturn(todayString)
        given(orderCacheRepository.getNextOrderNumber(todayString)).willReturn("${todayString}00000001")
        given(orderRepository.save(any<Order>())).willReturn(savedOrder)

        //when
        orderService.prepareOrder(createOrderRequest)

        //then
        assertThat(savedOrder.status).isEqualTo(OrderStatus.VALIDATING)
        assertThat(savedOrder.updatedAt).isEqualTo(LocalDateTime.of(2025, 8, 3, 9, 0, 0))
    }

    @Test
    fun `총액 매핑에 성공한다`() {
        //given
        val orderId = 1L
        val userId = 1000L
        val orderNo = "2025080300000001"
        val currentTime = LocalDateTime.of(2025, 8, 3, 9, 0, 0)
        val product1Price = 35000L
        val product2Price = 40000L
        val product1Quantity = 2
        val product2Quantity = 1
        val expectedTotal = product1Price * product1Quantity + product2Price * product2Quantity

        given(timeProvider.now()).willReturn(currentTime)

        val order = Order.create(userId, "박하나", orderNo, "카드", timeProvider).apply {
            ReflectionTestUtils.setField(this, "id", orderId)
            price = 0L
        }

        val orderItems = listOf(
            OrderItem(
                productId = 1L,
                productName = "로얄캐닌 강아지 사료",
                quantity = product1Quantity,
                price = 0L,
                orderId = orderId
            ).apply { ReflectionTestUtils.setField(this, "id", 1L) },
            OrderItem(
                productId = 2L,
                productName = "로얄캐닌 고양이 사료",
                quantity = product2Quantity,
                price = 0L,
                orderId = orderId
            ).apply { ReflectionTestUtils.setField(this, "id", 2L) }
        )

        val products = listOf(
            ProductInformationResponse(
                productId = 1L,
                productName = "로얄캐닌 강아지 사료",
                price = product1Price,
                stock = 100,
                quantity = product1Quantity
            ),
            ProductInformationResponse(
                productId = 2L,
                productName = "로얄캐닌 고양이 사료",
                price = product2Price,
                stock = 50,
                quantity = product2Quantity
            )
        )

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order))
        given(orderItemRepository.findAllByOrderId(orderId)).willReturn(orderItems)

        //when
        orderService.mappedTotalAmount(orderId, products)

        //then
        then(orderRepository).should().findById(orderId)
        then(orderItemRepository).should().findAllByOrderId(orderId)
        then(orderRepository).should().save(order)
        then(orderItemRepository).should().saveAll(orderItems)
        then(applicationEventPublisher).should().publishEvent(any<PaymentPrepareEvent>())

        assertThat(order.status).isEqualTo(OrderStatus.VALIDATION_SUCCESS)
        assertThat(order.price).isEqualTo(expectedTotal)
        assertThat(order.updatedAt).isEqualTo(currentTime)
        assertThat(orderItems[0].price).isEqualTo(product1Price)
        assertThat(orderItems[0].productName).isEqualTo(products[0].productName)
        assertThat(orderItems[1].price).isEqualTo(product2Price)
        assertThat(orderItems[1].productName).isEqualTo(products[1].productName)
    }

    @Test
    fun `이미 총액이 계산된 주문은 멱등 처리된다`() {
        //given
        val orderId = 1L
        val currentTime = LocalDateTime.of(2025, 8, 3, 9, 0, 0)
        val existingPrice = 50000L

        given(timeProvider.now()).willReturn(currentTime)

        val order = Order.create(1000L, "박하나", "2025080300000001", "카드", timeProvider).apply {
            ReflectionTestUtils.setField(this, "id", orderId)
            price = existingPrice
        }

        val products = listOf(
            ProductInformationResponse(
                productId = 1L,
                productName = "로얄캐닌 강아지 사료",
                price = 35000L,
                stock = 100,
                quantity = 2
            )
        )

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order))

        //when
        orderService.mappedTotalAmount(orderId, products)

        //then
        then(orderRepository).should().findById(orderId)
        then(orderItemRepository).shouldHaveNoInteractions()
        then(applicationEventPublisher).shouldHaveNoInteractions()

        assertThat(order.price).isEqualTo(existingPrice)
    }

    @Test
    fun `존재하지 않는 주문ID로 총액 매핑시 예외가 발생한다`() {
        //given
        val orderId = 999L
        val products = listOf(
            ProductInformationResponse(
                productId = 1L,
                productName = "로얄캐닌 강아지 사료",
                price = 35000L,
                stock = 100,
                quantity = 1
            )
        )

        given(orderRepository.findById(orderId)).willReturn(Optional.empty())

        //when & then
        try {
            orderService.mappedTotalAmount(orderId, products)
            assertThat(false).isTrue() // 예외가 발생해야 함
        } catch (e: ApplicationException) {
            assertThat(e.errorCode).isEqualTo(ErrorCode.ORDER_NOT_FOUND)
        }

        then(orderRepository).should().findById(orderId)
        then(orderItemRepository).shouldHaveNoInteractions()
        then(applicationEventPublisher).shouldHaveNoInteractions()
    }

    @Test
    fun `상품 검증 실패시 주문 상태가 VALIDATION_FAILED로 변경된다`() {
        //given
        val orderId = 1L
        val currentTime = LocalDateTime.of(2025, 8, 3, 9, 0, 0)
        val missingProductId = 999L

        given(timeProvider.now()).willReturn(currentTime)

        val order = Order.create(1000L, "박하나", "2025080300000001", "카드", timeProvider).apply {
            ReflectionTestUtils.setField(this, "id", orderId)
            price = 0L
        }

        val orderItems = listOf(
            OrderItem(
                productId = 1L,
                productName = "로얄캐닌 강아지 사료",
                quantity = 2,
                price = 0L,
                orderId = orderId
            ),
            OrderItem(
                productId = missingProductId,
                productName = "존재하지 않는 상품",
                quantity = 1,
                price = 0L,
                orderId = orderId
            )
        )

        val products = listOf(
            ProductInformationResponse(
                productId = 1L,
                productName = "로얄캐닌 강아지 사료",
                price = 35000L,
                stock = 100,
                quantity = 2
            )
        )

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order))
        given(orderItemRepository.findAllByOrderId(orderId)).willReturn(orderItems)

        //when
        orderService.mappedTotalAmount(orderId, products)

        //then
        then(orderRepository).should().findById(orderId)
        then(orderItemRepository).should().findAllByOrderId(orderId)
        then(orderRepository).should().save(order)
        then(orderItemRepository).shouldHaveNoMoreInteractions()
        then(applicationEventPublisher).shouldHaveNoInteractions()

        assertThat(order.status).isEqualTo(OrderStatus.VALIDATION_FAILED)
        assertThat(order.description).contains("존재하지 않는 상품 productId: [$missingProductId]")
        assertThat(order.updatedAt).isEqualTo(currentTime)
        assertThat(order.price).isEqualTo(0L)
    }

    @Test
    fun `재고 부족시 주문 상태가 VALIDATION_FAILED로 변경된다`() {
        //given
        val orderId = 1L
        val currentTime = LocalDateTime.of(2025, 8, 3, 9, 0, 0)
        val productId = 1L
        val orderQuantity = 10
        val availableStock = 5

        given(timeProvider.now()).willReturn(currentTime)

        val order = Order.create(1000L, "박하나", "2025080300000001", "카드", timeProvider).apply {
            ReflectionTestUtils.setField(this, "id", orderId)
            price = 0L
        }

        val orderItems = listOf(
            OrderItem(
                productId = productId,
                productName = "로얄캐닌 강아지 사료",
                quantity = orderQuantity,
                price = 0L,
                orderId = orderId
            )
        )

        val products = listOf(
            ProductInformationResponse(
                productId = productId,
                productName = "로얄캐닌 강아지 사료",
                price = 35000L,
                stock = availableStock,
                quantity = orderQuantity
            )
        )

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order))
        given(orderItemRepository.findAllByOrderId(orderId)).willReturn(orderItems)

        //when
        orderService.mappedTotalAmount(orderId, products)

        //then
        then(orderRepository).should().findById(orderId)
        then(orderItemRepository).should().findAllByOrderId(orderId)
        then(orderRepository).should().save(order)
        then(orderItemRepository).shouldHaveNoMoreInteractions()
        then(applicationEventPublisher).shouldHaveNoInteractions()

        assertThat(order.status).isEqualTo(OrderStatus.VALIDATION_FAILED)
        assertThat(order.description).contains("재고 부족 productId: $productId, 요청수량: $orderQuantity, 재고: $availableStock")
        assertThat(order.updatedAt).isEqualTo(currentTime)
        assertThat(order.price).isEqualTo(0L)
    }

    @Test
    fun `상품정보를 불러오지 못한경우 주문 상태가 VALIDATION_FAILED로 변경된다`() {
        //given
        val orderId = 1L
        val currentTime = LocalDateTime.of(2025, 8, 3, 9, 0, 0)

        given(timeProvider.now()).willReturn(currentTime)

        val order = Order.create(1000L, "박하나", "2025080300000001", "카드", timeProvider).apply {
            ReflectionTestUtils.setField(this, "id", orderId)
            price = 0L
        }

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order))

        //when
        orderService.validationFail(orderId)

        //then
        then(orderRepository).should().findById(orderId)
        then(orderRepository).should().save(order)

        assertThat(order.status).isEqualTo(OrderStatus.VALIDATION_FAILED)
    }

    @Test
    fun `결제금액 매핑중 예외가 발생하면 주문상태가 PROCESSING_FAILED로 변경된다`() {
        //given
        val orderId = 1L
        val currentTime = LocalDateTime.of(2025, 8, 3, 9, 0, 0)

        given(timeProvider.now()).willReturn(currentTime)

        val order = Order.create(1000L, "박하나", "2025080300000001", "카드", timeProvider).apply {
            ReflectionTestUtils.setField(this, "id", orderId)
            price = 0L
        }

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order))

        //when
        orderService.orderProcessFail(orderId)

        //then
        then(orderRepository).should().findById(orderId)
        then(orderRepository).should().save(order)

        assertThat(order.status).isEqualTo(OrderStatus.PROCESSING_FAILED)


    }

    @Test
    fun `결제 정보 저장 중 예외가 발생하면 PAYMENT_PREPARE_FAIL로 변경된다`() {
        //given
        val orderId = 1L
        val currentTime = LocalDateTime.of(2025, 8, 3, 9, 0, 0)

        given(timeProvider.now()).willReturn(currentTime)

        val order = Order.create(1000L, "박하나", "2025080300000001", "카드", timeProvider).apply {
            ReflectionTestUtils.setField(this, "id", orderId)
            price = 0L
        }

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order))

        //when
        orderService.paymentPrepareFail(orderId)

        //then
        then(orderRepository).should().findById(orderId)
        then(orderRepository).should().save(order)

        assertThat(order.status).isEqualTo(OrderStatus.PAYMENT_PREPARE_FAIL)
    }

    @Test
    fun `paymentId 매핑에 성공한다`() {
        //given
        val orderId = 1L
        val paymentId = 2000L
        val currentTime = LocalDateTime.of(2025, 8, 3, 9, 0, 0)

        given(timeProvider.now()).willReturn(currentTime)

        val order = Order.create(1000L, "박하나", "2025080300000001", "카드", timeProvider).apply {
            ReflectionTestUtils.setField(this, "id", orderId)
        }

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order))

        //when
        orderService.mappedPaymentId(orderId, paymentId)

        //then
        then(orderRepository).should().findById(orderId)
        then(orderRepository).should().save(order)

        assertThat(order.paymentId).isEqualTo(paymentId)
        assertThat(order.status).isEqualTo(OrderStatus.PREPARED)
        assertThat(order.updatedAt).isEqualTo(currentTime)
    }

    @Test
    fun `이미 paymentId가 매핑된 주문은 멱등 처리된다`() {
        //given
        val orderId = 1L
        val existingPaymentId = 1500L
        val newPaymentId = 2000L
        val currentTime = LocalDateTime.of(2025, 8, 3, 9, 0, 0)

        given(timeProvider.now()).willReturn(currentTime)

        val order = Order.create(1000L, "박하나", "2025080300000001", "카드", timeProvider).apply {
            ReflectionTestUtils.setField(this, "id", orderId)
            this.paymentId = existingPaymentId
        }

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order))

        //when
        orderService.mappedPaymentId(orderId, newPaymentId)

        //then
        then(orderRepository).should().findById(orderId)
        then(orderRepository).shouldHaveNoMoreInteractions()

        assertThat(order.paymentId).isEqualTo(existingPaymentId)
    }

    @Test
    fun `주문 상태 조회에 성공한다 - PREPARED 상태`() {
        //given
        val orderNo = "2025080300000001"
        val orderPrice = 110000L
        val currentTime = LocalDateTime.of(2025, 8, 3, 9, 0, 0)

        given(timeProvider.now()).willReturn(currentTime)

        val order = Order.create(1000L, "박하나", orderNo, "카드", timeProvider).apply {
            ReflectionTestUtils.setField(this, "id", 1L)
            price = orderPrice
            updateStatus(OrderStatus.PREPARED, timeProvider)
        }

        given(orderRepository.findByOrderNo(orderNo)).willReturn(order)

        //when
        val result = orderService.getStatus(orderNo)

        //then
        then(orderRepository).should().findByOrderNo(orderNo)

        assertThat(result.orderNo).isEqualTo(orderNo)
        assertThat(result.status).isEqualTo(OrderStatus.PREPARED)
        assertThat(result.amount).isEqualTo(orderPrice)
        assertThat(result.errorMessage).isNull()
    }

    @Test
    fun `주문 상태 조회에 성공한다 - CONFIRMED 상태`() {
        //given
        val orderNo = "2025080300000001"
        val orderPrice = 110000L
        val currentTime = LocalDateTime.of(2025, 8, 3, 9, 0, 0)

        given(timeProvider.now()).willReturn(currentTime)

        val order = Order.create(1000L, "박하나", orderNo, "카드", timeProvider).apply {
            ReflectionTestUtils.setField(this, "id", 1L)
            price = orderPrice
            updateStatus(OrderStatus.CONFIRMED, timeProvider)
        }

        given(orderRepository.findByOrderNo(orderNo)).willReturn(order)

        //when
        val result = orderService.getStatus(orderNo)

        //then
        then(orderRepository).should().findByOrderNo(orderNo)

        assertThat(result.orderNo).isEqualTo(orderNo)
        assertThat(result.status).isEqualTo(OrderStatus.CONFIRMED)
        assertThat(result.amount).isEqualTo(orderPrice)
        assertThat(result.errorMessage).isNull()
    }

    @Test
    fun `주문 상태 조회에 성공한다 - 지정된 상태가 아니면 금액을 null로 반환한다`() {
        //given
        val orderNo = "2025080300000001"
        val orderPrice = 110000L
        val errorMessage = "결제 실패"
        val currentTime = LocalDateTime.of(2025, 8, 3, 9, 0, 0)

        given(timeProvider.now()).willReturn(currentTime)

        val order = Order.create(1000L, "박하나", orderNo, "카드", timeProvider).apply {
            ReflectionTestUtils.setField(this, "id", 1L)
            price = orderPrice
            description = errorMessage
            updateStatus(OrderStatus.PAYMENT_FAILED, timeProvider)
        }

        given(orderRepository.findByOrderNo(orderNo)).willReturn(order)

        //when
        val result = orderService.getStatus(orderNo)

        //then
        then(orderRepository).should().findByOrderNo(orderNo)

        assertThat(result.orderNo).isEqualTo(orderNo)
        assertThat(result.status).isEqualTo(OrderStatus.PAYMENT_FAILED)
        assertThat(result.amount).isNull()
        assertThat(result.errorMessage).isEqualTo(errorMessage)
    }

    @Test
    fun `존재하지 않는 주문번호로 상태 조회시 예외가 발생한다`() {
        //given
        val orderNo = "9999999999999999"

        given(orderRepository.findByOrderNo(orderNo)).willReturn(null)

        //when
        val result = assertThrows<ApplicationException> {
            orderService.getStatus(orderNo)
        }

        //then
        then(orderRepository).should().findByOrderNo(orderNo)
        assertThat(result.errorCode).isEqualTo(ErrorCode.ORDER_NOT_FOUND)
        assertThat(result.message).isEqualTo(ErrorCode.ORDER_NOT_FOUND.message)
    }

    @Test
    fun `재고차감 요청에 성공한다`() {
        //given
        given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 8, 3, 9, 0, 0))

        val order = Order.create(1000L, "박하나", "2025080300000001", "카드", timeProvider).apply {
            ReflectionTestUtils.setField(this, "id", 1L)
            ReflectionTestUtils.setField(this, "price", 365000L)
            updateStatus(OrderStatus.PREPARED, timeProvider)
        }

        given(orderRepository.findByOrderNo(order.orderNo)).willReturn(order)

        //when
        val result = orderService.OrderItemDecreaseRequest(
            ConfirmOrderRequest(
                paymentKey = "payment_key_123",
                orderId = order.orderNo,
                amount = order.price,
            )
        )

        //then
        then(orderRepository).should().findByOrderNo(order.orderNo)
        then(orderRepository).should().save(order)

        assertThat(order.status).isEqualTo(OrderStatus.DECREASE_STOCK)
        assertThat(result.success).isEqualTo(true)
        assertThat(result.orderNo).isEqualTo(order.orderNo)
    }

    @Test
    fun `이미 재고차감 요청이 되었다면 멱등처리가 된다`() {
        //given
        given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 8, 3, 9, 0, 0))

        val order = Order.create(1000L, "박하나", "2025080300000001", "카드", timeProvider).apply {
            ReflectionTestUtils.setField(this, "id", 1L)
            ReflectionTestUtils.setField(this, "price", 365000L)
            updateStatus(OrderStatus.CONFIRMED, timeProvider)
        }

        given(orderRepository.findByOrderNo(order.orderNo)).willReturn(order)

        //when
        val result = orderService.OrderItemDecreaseRequest(
            ConfirmOrderRequest(
                paymentKey = "payment_key_123",
                orderId = order.orderNo,
                amount = order.price,
            )
        )

        //then
        then(orderRepository).should().findByOrderNo(order.orderNo)
        then(orderRepository).shouldHaveNoMoreInteractions()

        assertThat(order.status).isEqualTo(OrderStatus.CONFIRMED)
        assertThat(result.success).isEqualTo(true)
        assertThat(result.orderNo).isEqualTo(order.orderNo)
        assertThat(result.message).isEqualTo("이미 결제 완료된 주문입니다.")

    }

    @Test
    fun `이미 재고차감 요청이 되었다면 멱등처리가 된다2`() {
        //given
        given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 8, 3, 9, 0, 0))

        val order = Order.create(1000L, "박하나", "2025080300000001", "카드", timeProvider).apply {
            ReflectionTestUtils.setField(this, "id", 1L)
            ReflectionTestUtils.setField(this, "price", 365000L)
            updateStatus(OrderStatus.DECREASE_STOCK, timeProvider)
        }

        given(orderRepository.findByOrderNo(order.orderNo)).willReturn(order)

        //when
        val result = orderService.OrderItemDecreaseRequest(
            ConfirmOrderRequest(
                paymentKey = "payment_key_123",
                orderId = order.orderNo,
                amount = order.price,
            )
        )

        //then
        then(orderRepository).should().findByOrderNo(order.orderNo)
        then(orderRepository).shouldHaveNoMoreInteractions()

        assertThat(order.status).isEqualTo(OrderStatus.DECREASE_STOCK)
        assertThat(result.success).isEqualTo(true)
        assertThat(result.orderNo).isEqualTo(order.orderNo)
        assertThat(result.message).isEqualTo("이미 결제 완료된 주문입니다.")

    }

    @Test
    fun `PREPARED 상태면 이미 재고차감 요청에 실패한다`() {
        //given
        given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 8, 3, 9, 0, 0))

        val order = Order.create(1000L, "박하나", "2025080300000001", "카드", timeProvider).apply {
            ReflectionTestUtils.setField(this, "id", 1L)
            ReflectionTestUtils.setField(this, "price", 365000L)
            updateStatus(OrderStatus.VALIDATING, timeProvider)
        }

        given(orderRepository.findByOrderNo(order.orderNo)).willReturn(order)

        //when
        val result = orderService.OrderItemDecreaseRequest(
            ConfirmOrderRequest(
                paymentKey = "payment_key_123",
                orderId = order.orderNo,
                amount = order.price,
            )
        )

        //then
        then(orderRepository).should().findByOrderNo(order.orderNo)
        then(orderRepository).shouldHaveNoMoreInteractions()

        assertThat(order.status).isEqualTo(OrderStatus.VALIDATING)
        assertThat(result.success).isEqualTo(false)
        assertThat(result.orderNo).isEqualTo(order.orderNo)
        assertThat(result.message).isEqualTo("결제가 가능한 상태가 아닙니다. [status=${order.status}]")

    }

    @Test
    fun `잘못된 주문번호로 재고차감 요청시 예외가 발생한다실패한다`() {
        //given
        given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 8, 3, 9, 0, 0))

        val order = Order.create(1000L, "박하나", "2025080300000001", "카드", timeProvider).apply {
            ReflectionTestUtils.setField(this, "id", 1L)
            ReflectionTestUtils.setField(this, "price", 365000L)
            updateStatus(OrderStatus.PREPARED, timeProvider)
        }

        given(orderRepository.findByOrderNo(order.orderNo)).willReturn(order)

        //when
        val result = orderService.OrderItemDecreaseRequest(
            ConfirmOrderRequest(
                paymentKey = "payment_key_123",
                orderId = order.orderNo,
                amount = order.price+99999999,
            )
        )

        //then
        then(orderRepository).should().findByOrderNo(order.orderNo)
        then(orderRepository).shouldHaveNoMoreInteractions()

        assertThat(order.status).isEqualTo(OrderStatus.PREPARED)
        assertThat(result.success).isEqualTo(false)
        assertThat(result.orderNo).isEqualTo(order.orderNo)
        assertThat(result.message).isEqualTo("결제 금액 불일치: expected=${order.price}, actual=${order.price+99999999}")

    }
    @Test
    fun `저장된 주문의 금액과 주문금액이 다르면 재고차감 요청에 실패한다`() {
        //given
        val orderNo = "2025080300000001"

        given(orderRepository.findByOrderNo(orderNo)).willReturn(null)

        //when
        val result = assertThrows<ApplicationException> { orderService.OrderItemDecreaseRequest(
            ConfirmOrderRequest(
                paymentKey = "payment_key_123",
                orderId = orderNo,
                amount = 36000L
            )
        ) }

        //then
        then(orderRepository).should().findByOrderNo(orderNo)
        then(orderRepository).shouldHaveNoMoreInteractions()

        assertThat(result.errorCode).isEqualTo(ErrorCode.ORDER_NOT_FOUND)
        assertThat(result.message).isEqualTo(ErrorCode.ORDER_NOT_FOUND.message)
    }

    @Test
    fun `주문 처리에 성공한다`() {
        //given
        val orderId = 1L
        val paymentId = 2000L
        val orderPrice = 110000L
        val paymentKey = "payment_key_123"
        val currentTime = LocalDateTime.of(2025, 8, 3, 9, 0, 0)

        given(timeProvider.now()).willReturn(currentTime)

        val order = Order.create(1000L, "박하나", "2025080300000001", "카드", timeProvider).apply {
            ReflectionTestUtils.setField(this, "id", orderId)
            this.paymentId = paymentId
            price = orderPrice
        }

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order))
        given(orderCacheRepository.findPaymentKeyByOrderId(orderId)).willReturn(paymentKey)

        //when
        orderService.processOrder(orderId)

        //then
        then(orderRepository).should().findById(orderId)
        then(orderCacheRepository).should().findPaymentKeyByOrderId(orderId)
        then(applicationEventPublisher).should().publishEvent(any<PaymentPendingEvent>())

        assertThat(order.status).isEqualTo(OrderStatus.PAYMENT_PENDING)
        assertThat(order.updatedAt).isEqualTo(currentTime)
    }

    @Test
    fun `존재하지 않는 주문ID로 주문 처리시 예외가 발생한다`() {
        //given
        val orderId = 999L

        given(orderRepository.findById(orderId)).willReturn(Optional.empty())

        //when & then
        val result = assertThrows<ApplicationException> {
            orderService.processOrder(orderId)
        }

        then(orderRepository).should().findById(orderId)
        then(orderCacheRepository).shouldHaveNoInteractions()
        then(applicationEventPublisher).shouldHaveNoInteractions()

        assertThat(result.errorCode).isEqualTo(ErrorCode.ORDER_NOT_FOUND)
        assertThat(result.message).isEqualTo(ErrorCode.ORDER_NOT_FOUND.message)
    }

    @Test
    fun `PaymentKey가 만료되었을 때 예외가 발생한다`() {
        //given
        val orderId = 1L
        val paymentId = 2000L
        val currentTime = LocalDateTime.of(2025, 8, 3, 9, 0, 0)

        given(timeProvider.now()).willReturn(currentTime)

        val order = Order.create(1000L, "박하나", "2025080300000001", "카드", timeProvider).apply {
            ReflectionTestUtils.setField(this, "id", orderId)
            this.paymentId = paymentId
            price = 110000L
        }

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order))
        given(orderCacheRepository.findPaymentKeyByOrderId(orderId)).willReturn(null)

        //when & then
        val result = assertThrows<ApplicationException> {
            orderService.processOrder(orderId)
        }

        then(orderRepository).should().findById(orderId)
        then(orderCacheRepository).should().findPaymentKeyByOrderId(orderId)
        then(applicationEventPublisher).shouldHaveNoInteractions()

        assertThat(result.errorCode).isEqualTo(ErrorCode.PAYMENT_KEY_EXPIRED)
        assertThat(result.message).isEqualTo("PaymentKey 만료 또는 누락")
        assertThat(order.status).isEqualTo(OrderStatus.PAYMENT_PENDING)
        assertThat(order.updatedAt).isEqualTo(currentTime)
    }

    @Test
    fun `재고차감에 실패하면 DECREASE_STOCK_FAIL 상태가 된다`() {
        //given
        given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 8, 3, 9, 0, 0))
        val order = Order.create(1000L, "박하나", "2025080300000001", "카드", timeProvider).apply {
            ReflectionTestUtils.setField(this, "id", 1L)
            ReflectionTestUtils.setField(this, "price", 365000L)
            ReflectionTestUtils.setField(this, "paymentId", 2L)
            updateStatus(OrderStatus.DECREASE_STOCK, timeProvider)
        }

        given(orderRepository.findById(order.id!!)).willReturn(Optional.of(order))


        //when
        orderService.decreaseStockFail(order.id!!)

        //then
        then(orderRepository).should().findById(order.id!!)
        then(orderRepository).should().save(order)

        assertThat(order.status).isEqualTo(OrderStatus.DECREASE_STOCK_FAIL)
        
    }
    
    @Test
    fun `결제에 실패하면 이루어지면 PAYMENT_FAILED 상태로 변경된다`() {
        //given
        given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 8, 3, 9, 0, 0))
        val order = Order.create(1000L, "박하나", "2025080300000001", "카드", timeProvider).apply {
            ReflectionTestUtils.setField(this, "id", 1L)
            ReflectionTestUtils.setField(this, "price", 365000L)
            ReflectionTestUtils.setField(this, "paymentId", 2L)
            updateStatus(OrderStatus.PAYMENT_PENDING, timeProvider)
        }
        given(orderRepository.findById(order.id!!)).willReturn(Optional.of(order))

        //when
        orderService.rollbackStockAndCancel(order.id!!)

        //then
        then(orderRepository).should().findById(order.id!!)
        then(orderRepository).should().save(order)

        assertThat(order.status).isEqualTo(OrderStatus.PAYMENT_FAILED)
    }

    @Test
    fun `결제가 완료되면 CONFIRMED 상태로 변경된다`() {
        //given
        given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 8, 3, 9, 0, 0))
        val order = Order.create(1000L, "박하나", "2025080300000001", "카드", timeProvider).apply {
            ReflectionTestUtils.setField(this, "id", 1L)
            ReflectionTestUtils.setField(this, "price", 365000L)
            ReflectionTestUtils.setField(this, "paymentId", 2L)
            updateStatus(OrderStatus.PAYMENT_PENDING, timeProvider)
        }
        given(orderRepository.findById(order.id!!)).willReturn(Optional.of(order))


        //when
        orderService.confirmedOrder(order.id!!)

        //then
        then(orderRepository).should().findById(order.id!!)
        then(orderRepository).should().save(order)

        assertThat(order.status).isEqualTo(OrderStatus.CONFIRMED)
    }

    @Test
    fun `결제에 실패하면 PAYMENT_FAILED 상태로 변경된다`() {
        //given
        given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 8, 3, 9, 0, 0))
        val order = Order.create(1000L, "박하나", "2025080300000001", "카드", timeProvider).apply {
            ReflectionTestUtils.setField(this, "id", 1L)
            ReflectionTestUtils.setField(this, "price", 365000L)
            ReflectionTestUtils.setField(this, "paymentId", 2L)
            updateStatus(OrderStatus.PAYMENT_PENDING, timeProvider)
        }
        given(orderRepository.findById(order.id!!)).willReturn(Optional.of(order))

        //when
        orderService.failOrder(order.id!!)

        //then
        then(orderRepository).should().findById(order.id!!)
        then(orderRepository).should().save(order)

        assertThat(order.status).isEqualTo(OrderStatus.PAYMENT_FAILED)
    }
    @Test
    fun `결제를 취소하면 CANCLED 상태로 변경된다`() {
        //given
        given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 8, 3, 9, 0, 0))
        val order = Order.create(1000L, "박하나", "2025080300000001", "카드", timeProvider).apply {
            ReflectionTestUtils.setField(this, "id", 1L)
            ReflectionTestUtils.setField(this, "price", 365000L)
            ReflectionTestUtils.setField(this, "paymentId", 2L)
            updateStatus(OrderStatus.CONFIRMED, timeProvider)
        }
        given(orderRepository.findById(order.id!!)).willReturn(Optional.of(order))

        //when
        orderService.canceledOrder(order.id!!)

        //then
        then(orderRepository).should().findById(order.id!!)
        then(orderRepository).should().save(order)

        assertThat(order.status).isEqualTo(OrderStatus.CANCELED)
    }

    @Test
    fun `결제 취소에 실패하면 FAIL 상태로 변경된다`() {
        //given
        val orderId = 1L
        val currentTime = LocalDateTime.of(2025, 8, 3, 9, 0, 0)
        val expectedDescription = "결제 취소 실패 - 수동 개입 필요 (결제 상태 확인 후 처리)"

        given(timeProvider.now()).willReturn(currentTime)

        val order = Order.create(1000L, "박하나", "2025080300000001", "카드", timeProvider).apply {
            ReflectionTestUtils.setField(this, "id", orderId)
            ReflectionTestUtils.setField(this, "price", 365000L)
            ReflectionTestUtils.setField(this, "paymentId", 2L)
            updateStatus(OrderStatus.PAYMENT_FAILED, timeProvider)
        }

        given(orderRepository.findById(orderId)).willReturn(Optional.of(order))

        //when
        orderService.canceledFailOrder(orderId)

        //then
        then(orderRepository).should().findById(orderId)
        then(orderRepository).should().save(order)

        assertThat(order.status).isEqualTo(OrderStatus.FAIL)
        assertThat(order.description).isEqualTo(expectedDescription)
        assertThat(order.updatedAt).isEqualTo(currentTime)
    }

    @Test
    fun `존재하지 않는 주문ID로 결제 취소 실패 처리시 예외가 발생한다`() {
        //given
        val orderId = 999L

        given(orderRepository.findById(orderId)).willReturn(Optional.empty())

        //when & then
        val result = assertThrows<ApplicationException> {
            orderService.canceledFailOrder(orderId)
        }

        then(orderRepository).should().findById(orderId)
        then(orderRepository).shouldHaveNoMoreInteractions()

        assertThat(result.errorCode).isEqualTo(ErrorCode.ORDER_NOT_FOUND)
        assertThat(result.message).isEqualTo(ErrorCode.ORDER_NOT_FOUND.message)
    }

    @Test
    fun `주문 목록 조회에 성공한다`() {
        //given
        val userId = 1000L
        val status = OrderStatus.CONFIRMED
        val searchQuery = "테스트"
        val pageNumber = 0
        val pageSize = 10

        val orderSearchCondition = OrderSearchCondition(
            userId = userId,
            status = status,
            searchQuery = searchQuery
        )
        val pageable = PageRequest.of(pageNumber, pageSize)

        val order1 = GetOrdersResponse(
            orderId = 1L,
            orderNo = "2025080300000001",
            userId = userId,
            userName = "박하나",
            status = status,
            price = 75000L,
            createdAt = LocalDateTime.of(2025, 8, 3, 9, 0, 0),
            paymentId = 2001L
        )

        val order2 = GetOrdersResponse(
            orderId = 2L,
            orderNo = "2025080300000002",
            userId = userId,
            userName = "박하나",
            status = status,
            price = 45000L,
            createdAt = LocalDateTime.of(2025, 8, 3, 10, 0, 0),
            paymentId = 2002L
        )

        val orderList = listOf(order1, order2)
        val expectedPage = PageImpl(orderList, pageable, orderList.size.toLong())

        given(orderRepository.searchOrders(orderSearchCondition, pageable)).willReturn(expectedPage)

        //when
        val result = orderService.getOrders(orderSearchCondition, pageable)

        //then
        then(orderRepository).should().searchOrders(orderSearchCondition, pageable)

        assertThat(result.content).hasSize(orderList.size)
        assertThat(result.content[0].orderId).isEqualTo(order1.orderId)
        assertThat(result.content[0].orderNo).isEqualTo(order1.orderNo)
        assertThat(result.content[0].userId).isEqualTo(order1.userId)
        assertThat(result.content[0].userName).isEqualTo(order1.userName)
        assertThat(result.content[0].status).isEqualTo(order1.status)
        assertThat(result.content[0].price).isEqualTo(order1.price)
        assertThat(result.content[0].createdAt).isEqualTo(order1.createdAt)
        assertThat(result.content[0].paymentId).isEqualTo(order1.paymentId)
        assertThat(result.content[1].orderId).isEqualTo(order2.orderId)
        assertThat(result.content[1].orderNo).isEqualTo(order2.orderNo)
        assertThat(result.totalElements).isEqualTo(orderList.size.toLong())
        assertThat(result.number).isEqualTo(pageNumber)
        assertThat(result.size).isEqualTo(pageSize)
    }

    @Test
    fun `주문 목록 조회에 성공한다 - 빈 결과`() {
        //given
        val userId = 9999L
        val status = OrderStatus.PREPARED
        val searchQuery = "존재하지않는검색어"
        val pageNumber = 0
        val pageSize = 10

        val orderSearchCondition = OrderSearchCondition(
            userId = userId,
            status = status,
            searchQuery = searchQuery
        )
        val pageable = PageRequest.of(pageNumber, pageSize)

        val emptyOrderList = emptyList<GetOrdersResponse>()
        val expectedPage = PageImpl(emptyOrderList, pageable, 0L)

        given(orderRepository.searchOrders(orderSearchCondition, pageable)).willReturn(expectedPage)

        //when
        val result = orderService.getOrders(orderSearchCondition, pageable)

        //then
        then(orderRepository).should().searchOrders(orderSearchCondition, pageable)

        assertThat(result.content).isEmpty()
        assertThat(result.totalElements).isEqualTo(0L)
        assertThat(result.number).isEqualTo(pageNumber)
        assertThat(result.size).isEqualTo(pageSize)
    }

    @Test
    fun `주문 목록 조회에 성공한다 - 모든 검색 조건이 null인 경우`() {
        //given
        val pageNumber = 0
        val pageSize = 20

        val orderSearchCondition = OrderSearchCondition(
            userId = null,
            status = null,
            searchQuery = null
        )
        val pageable = PageRequest.of(pageNumber, pageSize)

        val order1 = GetOrdersResponse(
            orderId = 1L,
            orderNo = "2025080300000001",
            userId = 1000L,
            userName = "박하나",
            status = OrderStatus.CONFIRMED,
            price = 75000L,
            createdAt = LocalDateTime.of(2025, 8, 3, 9, 0, 0),
            paymentId = 2001L
        )

        val order2 = GetOrdersResponse(
            orderId = 2L,
            orderNo = "2025080300000002",
            userId = 2000L,
            userName = "김철수",
            status = OrderStatus.PREPARED,
            price = 45000L,
            createdAt = LocalDateTime.of(2025, 8, 3, 10, 0, 0),
            paymentId = 2002L
        )

        val orderList = listOf(order1, order2)
        val expectedPage = PageImpl(orderList, pageable, orderList.size.toLong())

        given(orderRepository.searchOrders(orderSearchCondition, pageable)).willReturn(expectedPage)

        //when
        val result = orderService.getOrders(orderSearchCondition, pageable)

        //then
        then(orderRepository).should().searchOrders(orderSearchCondition, pageable)

        assertThat(result.content).hasSize(orderList.size)
        assertThat(result.content[0].userId).isEqualTo(order1.userId)
        assertThat(result.content[1].userId).isEqualTo(order2.userId)
        assertThat(result.totalElements).isEqualTo(orderList.size.toLong())
    }

    @Test
    fun `주문 아이템 조회에 성공한다`() {
        //given
        val orderNo = "2025080300000001"
        val orderId = 1L
        val orderPrice = 110000L
        val product1Id = 1L
        val product1Name = "로얄캐닌 강아지 사료"
        val product1Price = 35000L
        val product1Quantity = 2
        val product2Id = 2L
        val product2Name = "로얄캐닌 고양이 사료"
        val product2Price = 40000L
        val product2Quantity = 1

        given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 8, 3, 9, 0, 0))

        val order = Order.create(1000L, "박하나", orderNo, "카드", timeProvider).apply {
            ReflectionTestUtils.setField(this, "id", orderId)
            price = orderPrice
        }

        val orderItems = listOf(
            OrderItem(
                productId = product1Id,
                productName = product1Name,
                quantity = product1Quantity,
                price = product1Price,
                orderId = orderId
            ).apply { ReflectionTestUtils.setField(this, "id", 1L) },
            OrderItem(
                productId = product2Id,
                productName = product2Name,
                quantity = product2Quantity,
                price = product2Price,
                orderId = orderId
            ).apply { ReflectionTestUtils.setField(this, "id", 2L) }
        )

        given(orderRepository.findByOrderNo(orderNo)).willReturn(order)
        given(orderItemRepository.findAllByOrderId(orderId)).willReturn(orderItems)

        //when
        val result = orderService.getOrderItems(orderNo)

        //then
        then(orderRepository).should().findByOrderNo(orderNo)
        then(orderItemRepository).should().findAllByOrderId(orderId)

        assertThat(result).hasSize(orderItems.size)
        assertThat(result[0].productId).isEqualTo(product1Id)
        assertThat(result[0].productName).isEqualTo(product1Name)
        assertThat(result[0].quantity).isEqualTo(product1Quantity)
        assertThat(result[0].unitPrice).isEqualTo(product1Price)
        assertThat(result[0].lineTotal).isEqualTo(product1Price * product1Quantity)
        assertThat(result[1].productId).isEqualTo(product2Id)
        assertThat(result[1].productName).isEqualTo(product2Name)
        assertThat(result[1].quantity).isEqualTo(product2Quantity)
        assertThat(result[1].unitPrice).isEqualTo(product2Price)
        assertThat(result[1].lineTotal).isEqualTo(product2Price * product2Quantity)
    }

    @Test
    fun `존재하지 않는 주문번호로 주문 아이템 조회시 예외가 발생한다`() {
        //given
        val orderNo = "9999999999999999"

        given(orderRepository.findByOrderNo(orderNo)).willReturn(null)

        //when & then
        val result = assertThrows<ApplicationException> {
            orderService.getOrderItems(orderNo)
        }

        then(orderRepository).should().findByOrderNo(orderNo)
        then(orderItemRepository).shouldHaveNoInteractions()

        assertThat(result.errorCode).isEqualTo(ErrorCode.ORDER_NOT_FOUND)
        assertThat(result.message).isEqualTo(ErrorCode.ORDER_NOT_FOUND.message)
    }

    @Test
    fun `주문 아이템 조회에 성공한다 - 빈 주문 아이템`() {
        //given
        val orderNo = "2025080300000001"
        val orderId = 1L
        val orderPrice = 0L

        given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 8, 3, 9, 0, 0))

        val order = Order.create(1000L, "박하나", orderNo, "카드", timeProvider).apply {
            ReflectionTestUtils.setField(this, "id", orderId)
            price = orderPrice
        }

        val emptyOrderItems = emptyList<OrderItem>()

        given(orderRepository.findByOrderNo(orderNo)).willReturn(order)
        given(orderItemRepository.findAllByOrderId(orderId)).willReturn(emptyOrderItems)

        //when
        val result = orderService.getOrderItems(orderNo)

        //then
        then(orderRepository).should().findByOrderNo(orderNo)
        then(orderItemRepository).should().findAllByOrderId(orderId)

        assertThat(result).isEmpty()
    }

    @Test
    fun `주문 아이템 조회에 성공한다 - 총액 불일치 경고 로그 테스트`() {
        //given
        val orderNo = "2025080300000001"
        val orderId = 1L
        val orderPrice = 100000L
        val product1Price = 35000L
        val product1Quantity = 2
        val actualTotal = product1Price * product1Quantity

        given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 8, 3, 9, 0, 0))

        val order = Order.create(1000L, "박하나", orderNo, "카드", timeProvider).apply {
            ReflectionTestUtils.setField(this, "id", orderId)
            price = orderPrice
        }

        val orderItems = listOf(
            OrderItem(
                productId = 1L,
                productName = "로얄캐닌 강아지 사료",
                quantity = product1Quantity,
                price = product1Price,
                orderId = orderId
            ).apply { ReflectionTestUtils.setField(this, "id", 1L) }
        )

        given(orderRepository.findByOrderNo(orderNo)).willReturn(order)
        given(orderItemRepository.findAllByOrderId(orderId)).willReturn(orderItems)

        //when
        val result = orderService.getOrderItems(orderNo)

        //then
        then(orderRepository).should().findByOrderNo(orderNo)
        then(orderItemRepository).should().findAllByOrderId(orderId)

        assertThat(result).hasSize(orderItems.size)
        assertThat(result[0].productId).isEqualTo(orderItems[0].productId)
        assertThat(result[0].lineTotal).isEqualTo(actualTotal)
    }
}

