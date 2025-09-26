package hana.lovepet.orderservice.integration

import com.fasterxml.jackson.databind.ObjectMapper
import hana.lovepet.orderservice.api.domain.Order
import hana.lovepet.orderservice.api.domain.OrderItem
import hana.lovepet.orderservice.api.domain.constant.OrderStatus
import hana.lovepet.orderservice.api.repository.OrderCacheRepository
import hana.lovepet.orderservice.api.repository.OrderItemRepository
import hana.lovepet.orderservice.api.repository.OrderRepository
import hana.lovepet.orderservice.common.clock.TimeProvider
import hana.lovepet.orderservice.infrastructure.kafka.Topics
import hana.lovepet.orderservice.infrastructure.kafka.`in`.dto.*
import hana.lovepet.orderservice.infrastructure.kafka.out.dto.PaymentCancelEvent
import hana.lovepet.orderservice.infrastructure.kafka.out.dto.PaymentPendingEvent
import hana.lovepet.orderservice.infrastructure.kafka.out.dto.PaymentPrepareEvent
import hana.lovepet.orderservice.infrastructure.kafka.out.dto.ProductStockRollbackEvent
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.kafka.test.utils.KafkaTestUtils
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.Duration
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.TimeUnit

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@EnableKafka
@EmbeddedKafka(
    partitions = 1,
    brokerProperties = ["listeners=PLAINTEXT://localhost:0", "port=0"],
    topics = [
        Topics.PRODUCT_INFORMATION_REQUEST,
        Topics.PRODUCT_INFORMATION_REQUEST + "-dlt",
        Topics.PRODUCT_INFORMATION_RESPONSE,
        Topics.PRODUCT_INFORMATION_RESPONSE + "-dlt",
        Topics.PAYMENT_PREPARE,
        Topics.PAYMENT_PREPARE + "-dlt",
        Topics.PAYMENT_PREPARED,
        Topics.PAYMENT_PREPARED + "-dlt",
        Topics.PAYMENT_PREPARE_FAIL,
        Topics.PAYMENT_PREPARE_FAIL + "-dlt",
        Topics.PRODUCT_STOCK_DECREASE,
        Topics.PRODUCT_STOCK_DECREASE + "-dlt",
        Topics.PRODUCT_STOCK_DECREASED,
        Topics.PRODUCT_STOCK_DECREASED + "-dlt",
        Topics.PAYMENT_PENDING,
        Topics.PAYMENT_PENDING + "-dlt",
        Topics.PAYMENT_CONFIRMED,
        Topics.PAYMENT_CONFIRMED + "-dlt",
        Topics.PAYMENT_CONFIRMED_FAIL,
        Topics.PAYMENT_CONFIRMED_FAIL + "-dlt",
        Topics.PAYMENT_CANCEL,
        Topics.PAYMENT_CANCEL + "-dlt",
        Topics.PAYMENT_CANCELED,
        Topics.PAYMENT_CANCELED + "-dlt",
        Topics.PAYMENT_CANCELED_FAIL,
        Topics.PAYMENT_CANCELED_FAIL + "-dlt",
        Topics.PRODUCT_STOCK_ROLLBACK,
        Topics.PRODUCT_STOCK_ROLLBACK + "-dlt"
    ]
)
@ActiveProfiles("test")
class KafkaEventTest {

    @Autowired
    lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @Autowired
    lateinit var om: ObjectMapper

    @Autowired
    lateinit var orderRepository: OrderRepository

    @Autowired
    lateinit var orderItemRepository: OrderItemRepository

    @Autowired
    lateinit var timeProvider: TimeProvider

    @MockitoBean
    lateinit var orderCacheRepository: OrderCacheRepository

    @MockitoBean
    lateinit var redisConnectionFactory: RedisConnectionFactory

    @MockitoBean
    lateinit var paymentKeyRedisTemplate: RedisTemplate<String, String>

    @Value("\${spring.embedded.kafka.brokers}")
    lateinit var brokers: String

    lateinit var consumer: Consumer<String,  String>

    @BeforeEach
    fun setup() {
        val consumerProps = KafkaTestUtils.consumerProps(brokers, "test-order-service", "true")
        consumerProps[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"

        val consumerFactory = DefaultKafkaConsumerFactory(
            consumerProps,
            StringDeserializer(),
            StringDeserializer()
        )

        consumer = consumerFactory.createConsumer()
        consumer.subscribe(
            listOf(
                Topics.PRODUCT_INFORMATION_REQUEST, Topics.PRODUCT_INFORMATION_REQUEST + "-dlt",
                Topics.PRODUCT_INFORMATION_RESPONSE, Topics.PRODUCT_INFORMATION_RESPONSE + "-dlt",
                Topics.PAYMENT_PREPARE_FAIL, Topics.PAYMENT_PREPARE_FAIL + "-dlt",
                Topics.PAYMENT_PREPARED, Topics.PAYMENT_PREPARED + "-dlt",
                Topics.PAYMENT_PREPARE, Topics.PAYMENT_PREPARE + "-dlt",
                Topics.PRODUCT_STOCK_DECREASE, Topics.PRODUCT_STOCK_DECREASE + "-dlt",
                Topics.PRODUCT_STOCK_DECREASED, Topics.PRODUCT_STOCK_DECREASED + "-dlt",
                Topics.PAYMENT_CONFIRMED, Topics.PAYMENT_CONFIRMED + "-dlt",
                Topics.PAYMENT_CONFIRMED_FAIL, Topics.PAYMENT_CONFIRMED_FAIL + "-dlt",
                Topics.PAYMENT_PENDING, Topics.PAYMENT_PENDING + "-dlt",
                Topics.PAYMENT_CANCEL, Topics.PAYMENT_CANCEL + "-dlt",
                Topics.PAYMENT_CANCELED, Topics.PAYMENT_CANCELED + "-dlt",
                Topics.PAYMENT_CANCELED_FAIL, Topics.PAYMENT_CANCELED_FAIL + "-dlt",
                Topics.PRODUCT_STOCK_ROLLBACK, Topics.PRODUCT_STOCK_ROLLBACK + "-dlt"
            )
        )

        orderRepository.deleteAll()
        orderItemRepository.deleteAll()
    }

    @AfterEach
    fun tearDown() {
        consumer.close()
    }

    @Test
    @Timeout(10, unit = TimeUnit.SECONDS)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    fun `상품 정보 응답 이벤트 처리 후 결제 준비 토픽 발행 - 성공`() {
        // given
        val savedOrder = createTestOrder(1234L)

        val event = ProductsInformationResponseEvent(
            eventId = UUID.randomUUID().toString(),
            orderId = savedOrder.id!!,
            success = true,
            products = listOf(
                ProductsInformationResponseEvent.ProductInformationResponse(
                    productId = 1L,
                    productName = "테스트 상품",
                    price = 10000,
                    quantity = 2,
                    stock = 100
                )
            ),
            errorMessage = null
        )
        val json = om.writeValueAsString(event)

        // when
        kafkaTemplate.send(Topics.PRODUCT_INFORMATION_RESPONSE, event.orderId.toString(), json)
        kafkaTemplate.flush()

        Thread.sleep(3000) // 처리 시간 대기

        // then
        val record: ConsumerRecord<String, String> =
            KafkaTestUtils.getSingleRecord(consumer, Topics.PAYMENT_PREPARE, Duration.ofSeconds(5))

        val prepareEvent = om.readValue(record.value(), PaymentPrepareEvent::class.java)
        assertThat(prepareEvent.orderId).isEqualTo(event.orderId)

        // 주문이 업데이트되었는지 확인
        val updatedOrder = orderRepository.findById(savedOrder.id!!).orElseThrow()
        assertThat(updatedOrder.price).isEqualTo(20000) // 10000 * 2
    }

    @Test
    @Timeout(10, unit = TimeUnit.SECONDS)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    fun `상품 정보 응답 이벤트 처리 - 실패`() {
        // given
        val savedOrder = createTestOrder(5678L)

        val event = ProductsInformationResponseEvent(
            eventId = UUID.randomUUID().toString(),
            orderId = savedOrder.id!!,
            success = false,
            products = emptyList(),
            errorMessage = "상품을 찾을 수 없습니다"
        )
        val json = om.writeValueAsString(event)

        // when
        kafkaTemplate.send(Topics.PRODUCT_INFORMATION_RESPONSE, event.orderId.toString(), json)
        kafkaTemplate.flush()

        // then
        Thread.sleep(3000) // 처리 시간 대기

        val updatedOrder = orderRepository.findById(savedOrder.id!!).orElseThrow()
        assertThat(updatedOrder.status).isEqualTo(OrderStatus.VALIDATION_FAILED)
    }

    @Test
    @Timeout(10, unit = TimeUnit.SECONDS)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    fun `상품 정보 응답 처리 실패시 DLT 토픽으로 이동`() {
        // given
        val event = ProductsInformationResponseEvent(
            eventId = UUID.randomUUID().toString(),
            orderId = 999999L, // 존재하지 않는 orderId 예외발생
            success = true,
            products = emptyList(),
            errorMessage = null
        )
        val json = om.writeValueAsString(event)

        // when
        kafkaTemplate.send(Topics.PRODUCT_INFORMATION_RESPONSE, event.orderId.toString(), json)
        kafkaTemplate.flush()


        // then
        val dltRecord = KafkaTestUtils.getSingleRecord(consumer, Topics.PRODUCT_INFORMATION_RESPONSE+"-dlt", Duration.ofSeconds(5))
        val failedEvent = om.readValue(dltRecord.value(), ProductsInformationResponseEvent::class.java)

        assertThat(failedEvent).isNotNull
        assertThat(failedEvent.orderId).isEqualTo(event.orderId)
    }


    @Test
    @Timeout(10, unit = TimeUnit.SECONDS)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    fun `결제 준비 실패 이벤트 처리`() {
        // given
        val savedOrder = createTestOrder(1234L)

        val event = PaymentPrepareFailEvent(
            eventId = UUID.randomUUID().toString(),
            orderId = savedOrder.id!!,
            idempotencyKey = "test-fail-key"
        )
        val json = om.writeValueAsString(event)

        // when
        kafkaTemplate.send(Topics.PAYMENT_PREPARE_FAIL, event.orderId.toString(), json)
        kafkaTemplate.flush()

        // then
        Thread.sleep(3000) // 처리 시간 대기

        val updatedOrder = orderRepository.findById(savedOrder.id!!).orElseThrow()
        assertThat(updatedOrder.status).isEqualTo(OrderStatus.PAYMENT_PREPARE_FAIL)
    }


    @Test
    @Timeout(10, unit = TimeUnit.SECONDS)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    fun `결제 준비 실패 이벤트 처리 실패시 DLT 토픽으로 이동`() {
        // given
        val event = PaymentPrepareFailEvent(
            eventId = UUID.randomUUID().toString(),
            orderId = 999999L, // 존재하지 않는 orderId 예외발생
            idempotencyKey = "test-fail-key"
        )
        val json = om.writeValueAsString(event)

        // when
        kafkaTemplate.send(Topics.PAYMENT_PREPARE_FAIL, event.orderId.toString(), json)
        kafkaTemplate.flush()


        // then
        val dltRecord = KafkaTestUtils.getSingleRecord(consumer, Topics.PAYMENT_PREPARE_FAIL+"-dlt", Duration.ofSeconds(5))
        val failedEvent = om.readValue(dltRecord.value(), PaymentPrepareFailEvent::class.java)

        assertThat(failedEvent).isNotNull
        assertThat(failedEvent.orderId).isEqualTo(event.orderId)
    }

    @Test
    @Timeout(10, unit = TimeUnit.SECONDS)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    fun `결제 준비 완료 이벤트 처리`() {
        // given
        val savedOrder = createTestOrder(9999L)
        val paymentId = 123L

        val event = PaymentPreparedEvent(
            eventId = UUID.randomUUID().toString(),
            occurredAt = LocalDateTime.now(),
            orderId = savedOrder.id!!,
            paymentId = paymentId,
            idempotencyKey = "test-key"
        )
        val json = om.writeValueAsString(event)

        // when
        kafkaTemplate.send(Topics.PAYMENT_PREPARED, event.orderId.toString(), json)
        kafkaTemplate.flush()

        // then
        Thread.sleep(3000) // 처리 시간 대기

        val updatedOrder = orderRepository.findById(savedOrder.id!!).orElseThrow()
        assertThat(updatedOrder.paymentId).isEqualTo(paymentId)
        assertThat(updatedOrder.status).isEqualTo(OrderStatus.PREPARED)
    }

    @Test
    @Timeout(10, unit = TimeUnit.SECONDS)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    fun `결제 준비 완료 이벤트 처리 실패시 DLT 토픽으로 이동`() {
        // given

        val paymentId = 123L

        val event = PaymentPreparedEvent(
            eventId = UUID.randomUUID().toString(),
            occurredAt = LocalDateTime.now(),
            orderId = 999999L, // 존재하지 않는 orderId 예외발생
            paymentId = paymentId,
            idempotencyKey = "test-key"
        )
        val json = om.writeValueAsString(event)

        // when
        kafkaTemplate.send(Topics.PAYMENT_PREPARED, event.orderId.toString(), json)
        kafkaTemplate.flush()


        // then
        val dltRecord = KafkaTestUtils.getSingleRecord(consumer, Topics.PAYMENT_PREPARED+"-dlt", Duration.ofSeconds(5))
        val failedEvent = om.readValue(dltRecord.value(), PaymentPreparedEvent::class.java)

        assertThat(failedEvent).isNotNull
        assertThat(failedEvent.orderId).isEqualTo(event.orderId)
    }

    @Test
    @Timeout(10, unit = TimeUnit.SECONDS)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    fun `재고 차감 성공 이벤트 처리 후 결제 대기 토픽 발행`() {
        // given
        val savedOrder = createTestOrder(1111L)
        savedOrder.paymentId = 123L // 결제 ID 설정
        orderRepository.save(savedOrder)

        given(orderCacheRepository.findPaymentKeyByOrderId(savedOrder.id!!)).willReturn("test-payment-key")

        val event = ProductStockDecreasedEvent(
            eventId = UUID.randomUUID().toString(),
            orderId = savedOrder.id!!,
            success = true,
            errorMessage = null,
            idempotencyKey = "test-stock-key"
        )
        val json = om.writeValueAsString(event)

        // when
        kafkaTemplate.send(Topics.PRODUCT_STOCK_DECREASED, event.orderId.toString(), json)
        kafkaTemplate.flush()

        // then
        Thread.sleep(3000)
        val record: ConsumerRecord<String, String> =
            KafkaTestUtils.getSingleRecord(consumer, Topics.PAYMENT_PENDING, Duration.ofSeconds(5))

        val pendingEvent = om.readValue(record.value(), PaymentPendingEvent::class.java)
        assertThat(pendingEvent.orderId).isEqualTo(event.orderId)

        val updatedOrder = orderRepository.findById(savedOrder.id!!).orElseThrow()
        assertThat(updatedOrder.status).isEqualTo(OrderStatus.PAYMENT_PENDING)
    }


    @Test
    @Timeout(10, unit = TimeUnit.SECONDS)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    fun `재고 차감 성공 이벤트 처리 실패시 DLT 토픽으로 이동`() {
        // given
        val savedOrder = createTestOrder(1111L)
        savedOrder.paymentId = 123L // 결제 ID 설정
        orderRepository.save(savedOrder)

        // 캐시 만료인한 장애상황 가정
        given(orderCacheRepository.findPaymentKeyByOrderId(savedOrder.id!!)).willReturn(null)

        val event = ProductStockDecreasedEvent(
            eventId = UUID.randomUUID().toString(),
            orderId = savedOrder.id!!,
            success = true,
            errorMessage = null,
            idempotencyKey = "test-stock-key"
        )
        val json = om.writeValueAsString(event)

        // when
        kafkaTemplate.send(Topics.PRODUCT_STOCK_DECREASED, event.orderId.toString(), json)
        kafkaTemplate.flush()


        // then
        val dltRecord = KafkaTestUtils.getSingleRecord(consumer, Topics.PRODUCT_STOCK_DECREASED+"-dlt", Duration.ofSeconds(5))
        val failedEvent = om.readValue(dltRecord.value(), ProductStockDecreasedEvent::class.java)

        assertThat(failedEvent).isNotNull
        assertThat(failedEvent.orderId).isEqualTo(event.orderId)
    }


    @Test
    @Timeout(10, unit = TimeUnit.SECONDS)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    fun `재고 차감 실패 이벤트 처리`() {
        // given
        val savedOrder = createTestOrder(2222L)
        savedOrder.paymentId = 123L
        orderRepository.save(savedOrder)

        val event = ProductStockDecreasedEvent(
            eventId = UUID.randomUUID().toString(),
            orderId = savedOrder.id!!,
            success = false,
            errorMessage = "재고 부족",
            idempotencyKey = "test-stock-fail-key"
        )

        given(orderCacheRepository.findPaymentKeyByOrderId(savedOrder.id!!)).willReturn("test-payment-key")

        val json = om.writeValueAsString(event)

        // when
        kafkaTemplate.send(Topics.PRODUCT_STOCK_DECREASED, event.orderId.toString(), json)
        kafkaTemplate.flush()

        // then
        Thread.sleep(3000) // 처리 시간 대기

        // 결제 취소 이벤트 발행 확인 -- 불필요한 기능으로 프러덕션코드에서 주석처리 하였음
//        val cancelRecord: ConsumerRecord<String, String> =
//            KafkaTestUtils.getSingleRecord(consumer, Topics.PAYMENT_CANCEL, Duration.ofSeconds(5))

//        val cancelEvent = om.readValue(cancelRecord.value(), PaymentCancelEvent::class.java)
//        assertThat(cancelEvent.orderId).isEqualTo(savedOrder.id!!)
//        assertThat(cancelEvent.paymentId).isEqualTo(123L)

        val updatedOrder = orderRepository.findById(savedOrder.id!!).orElseThrow()
        assertThat(updatedOrder.status).isEqualTo(OrderStatus.DECREASE_STOCK_FAIL)
    }

    @Test
    @Timeout(10, unit = TimeUnit.SECONDS)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    fun `재고 차감 실패 이벤트 처리 실패시 DLT 토픽으로 이동`() {
        // given
        val event = ProductStockDecreasedEvent(
            eventId = UUID.randomUUID().toString(),
            orderId = 999999L, // 존재하지 않는 orderId
            success = false,
            errorMessage = "재고 부족",
            idempotencyKey = "test-stock-fail-key"
        )
        val json = om.writeValueAsString(event)

        // when
        kafkaTemplate.send(Topics.PRODUCT_STOCK_DECREASED, event.orderId.toString(), json)
        kafkaTemplate.flush()

        // then
        val dltRecord = KafkaTestUtils.getSingleRecord(consumer, Topics.PRODUCT_STOCK_DECREASED+"-dlt", Duration.ofSeconds(5))
        val failedEvent = om.readValue(dltRecord.value(), ProductStockDecreasedEvent::class.java)

        assertThat(failedEvent).isNotNull
        assertThat(failedEvent.orderId).isEqualTo(event.orderId)
        assertThat(failedEvent.success).isFalse()
    }

    @Test
    @Timeout(10, unit = TimeUnit.SECONDS)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    fun `결제 확정 성공 이벤트 처리`() {
        // given
        val savedOrder = createTestOrder(3333L)
        savedOrder.paymentId = 456L
        orderRepository.save(savedOrder)

        val event = PaymentConfirmedEvent(
            eventId = UUID.randomUUID().toString(),
            occurredAt = LocalDateTime.now(),
            orderId = savedOrder.id!!,
            paymentId = 456L,
            idempotencyKey = "test-confirmed-key"
        )
        val json = om.writeValueAsString(event)

        // when
        kafkaTemplate.send(Topics.PAYMENT_CONFIRMED, event.orderId.toString(), json)
        kafkaTemplate.flush()

        // then
        Thread.sleep(3000) // 처리 시간 대기

        val updatedOrder = orderRepository.findById(savedOrder.id!!).orElseThrow()
        assertThat(updatedOrder.status).isEqualTo(OrderStatus.CONFIRMED)
    }

    @Test
    @Timeout(10, unit = TimeUnit.SECONDS)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    fun `결제 확정 성공 이벤트 처리 실패시 DLT 토픽으로 이동`() {
        // given
        val event = PaymentConfirmedEvent(
            eventId = UUID.randomUUID().toString(),
            occurredAt = LocalDateTime.now(),
            orderId = 999999L, // 존재하지 않는 orderId 예외발생
            paymentId = 456L,
            idempotencyKey = "test-confirmed-key"
        )
        val json = om.writeValueAsString(event)

        // when
        kafkaTemplate.send(Topics.PAYMENT_CONFIRMED, event.orderId.toString(), json)
        kafkaTemplate.flush()


        // then
        val dltRecord = KafkaTestUtils.getSingleRecord(consumer, Topics.PAYMENT_CONFIRMED+"-dlt", Duration.ofSeconds(5))
        val failedEvent = om.readValue(dltRecord.value(), PaymentConfirmedEvent::class.java)

        assertThat(failedEvent).isNotNull
        assertThat(failedEvent.orderId).isEqualTo(event.orderId)
    }

    @Test
    @Timeout(10, unit = TimeUnit.SECONDS)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    fun `결제 확정 실패 이벤트 처리 후 재고 롤백 이벤트 발행`() {
        // given
        val savedOrder = createTestOrder(4444L)
        savedOrder.paymentId = 789L
        orderRepository.save(savedOrder)

        val event = PaymentConfirmedFailEvent(
            eventId = UUID.randomUUID().toString(),
            orderId = savedOrder.id!!,
            idempotencyKey = "test-confirmed-fail-key"
        )
        val json = om.writeValueAsString(event)

        // when
        kafkaTemplate.send(Topics.PAYMENT_CONFIRMED_FAIL, event.orderId.toString(), json)
        kafkaTemplate.flush()

        // then
        Thread.sleep(3000) // 처리 시간 대기

        // 재고 롤백 이벤트 발행 확인
        val rollbackRecord: ConsumerRecord<String, String> =
            KafkaTestUtils.getSingleRecord(consumer, Topics.PRODUCT_STOCK_ROLLBACK, Duration.ofSeconds(5))

        val rollbackEvent = om.readValue(rollbackRecord.value(), ProductStockRollbackEvent::class.java)
        assertThat(rollbackEvent.orderId).isEqualTo(savedOrder.id!!)
        assertThat(rollbackEvent.products).hasSize(1)
        assertThat(rollbackEvent.products[0].productId).isEqualTo(1L)
        assertThat(rollbackEvent.products[0].quantity).isEqualTo(2)

        val updatedOrder = orderRepository.findById(savedOrder.id!!).orElseThrow()
        assertThat(updatedOrder.status).isEqualTo(OrderStatus.PAYMENT_FAILED)
    }

    @Test
    @Timeout(10, unit = TimeUnit.SECONDS)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    fun `결제 확정 실패 이벤트 처리 실패시 DLT 토픽으로 이동`() {
        // given
        val event = PaymentConfirmedFailEvent(
            eventId = UUID.randomUUID().toString(),
            orderId = 999999L, // 존재하지 않는 orderId 예외발생
            idempotencyKey = "test-confirmed-fail-key"
        )
        val json = om.writeValueAsString(event)

        // when
        kafkaTemplate.send(Topics.PAYMENT_CONFIRMED_FAIL, event.orderId.toString(), json)
        kafkaTemplate.flush()

        // then
        val dltRecord = KafkaTestUtils.getSingleRecord(consumer, Topics.PAYMENT_CONFIRMED_FAIL+"-dlt", Duration.ofSeconds(5))
        val failedEvent = om.readValue(dltRecord.value(), PaymentConfirmedFailEvent::class.java)

        assertThat(failedEvent).isNotNull
        assertThat(failedEvent.orderId).isEqualTo(event.orderId)
    }



    @Test
    @Timeout(10, unit = TimeUnit.SECONDS)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    fun `결제 취소 성공 이벤트 처리`() {
        // given
        val savedOrder = createTestOrder(5555L)
        savedOrder.status = OrderStatus.CONFIRMED
        savedOrder.paymentId = 999L
        orderRepository.save(savedOrder)

        val event = PaymentCanceledEvent(
            eventId = UUID.randomUUID().toString(),
            cancelAt = LocalDateTime.now(),
            orderId = savedOrder.id!!,
            paymentId = 999L,
            idempotencyKey = "test-canceled-key"
        )
        val json = om.writeValueAsString(event)

        // when
        kafkaTemplate.send(Topics.PAYMENT_CANCELED, event.orderId.toString(), json)
        kafkaTemplate.flush()

        // then
        Thread.sleep(3000) // 처리 시간 대기

        val updatedOrder = orderRepository.findById(savedOrder.id!!).orElseThrow()
        assertThat(updatedOrder.status).isEqualTo(OrderStatus.CANCELED)
    }

    @Test
    @Timeout(10, unit = TimeUnit.SECONDS)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    fun `결제 취소 성공 이벤트 처리 실패시 DLT 토픽 발행`() {
        // given
        val event = PaymentCanceledEvent(
            eventId = UUID.randomUUID().toString(),
            cancelAt = LocalDateTime.now(),
            orderId = 999999L, // 존재하지 않는 orderId 예외발생
            paymentId = 999L,
            idempotencyKey = "test-canceled-key"
        )
        val json = om.writeValueAsString(event)

        // when
        kafkaTemplate.send(Topics.PAYMENT_CANCELED, event.orderId.toString(), json)
        kafkaTemplate.flush()

        // then
        val dltRecord = KafkaTestUtils.getSingleRecord(consumer, Topics.PAYMENT_CANCELED+"-dlt", Duration.ofSeconds(5))
        val failedEvent = om.readValue(dltRecord.value(), PaymentCanceledEvent::class.java)

        assertThat(failedEvent).isNotNull
        assertThat(failedEvent.orderId).isEqualTo(event.orderId)

    }


    @Test
    @Timeout(10, unit = TimeUnit.SECONDS)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    fun `결제 취소 실패 이벤트 처리`() {
        // given
        val savedOrder = createTestOrder(6666L)
        savedOrder.status = OrderStatus.CONFIRMED
        savedOrder.paymentId = 888L
        orderRepository.save(savedOrder)

        val event = PaymentCanceledFailEvent(
            eventId = UUID.randomUUID().toString(),
            orderId = savedOrder.id!!,
            idempotencyKey = "test-canceled-fail-key"
        )
        val json = om.writeValueAsString(event)

        // when
        kafkaTemplate.send(Topics.PAYMENT_CANCELED_FAIL, event.orderId.toString(), json)
        kafkaTemplate.flush()

        // then
        Thread.sleep(3000) // 처리 시간 대기

        val updatedOrder = orderRepository.findById(savedOrder.id!!).orElseThrow()
        assertThat(updatedOrder.status).isEqualTo(OrderStatus.FAIL)
    }



    @Test
    @Timeout(10, unit = TimeUnit.SECONDS)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    fun `결제 취소 실패 이벤트 처리 실패시 DLT 토픽으로 이동`() {
        // given
        val event = PaymentCanceledFailEvent(
            eventId = UUID.randomUUID().toString(),
            orderId = 999999L, // 존재하지 않는 orderId 예외발생
            idempotencyKey = "test-canceled-fail-key"
        )
        val json = om.writeValueAsString(event)

        // when
        kafkaTemplate.send(Topics.PAYMENT_CANCELED_FAIL, event.orderId.toString(), json)
        kafkaTemplate.flush()

        // then
        val dltRecord = KafkaTestUtils.getSingleRecord(consumer, Topics.PAYMENT_CANCELED_FAIL+"-dlt", Duration.ofSeconds(5))
        val failedEvent = om.readValue(dltRecord.value(), PaymentCanceledFailEvent::class.java)

        assertThat(failedEvent).isNotNull
        assertThat(failedEvent.orderId).isEqualTo(event.orderId)

    }


    private fun createTestOrder(orderNo: Long): Order {
        val order = Order.create(
            userId = 1234L,
            userName = "테스트유저",
            orderNo = "ORDER-$orderNo",
            paymentMethod = "카드",
            timeProvider = timeProvider
        )

        // Order를 먼저 저장해서 ID를 생성
        val savedOrder = orderRepository.save(order)

        // OrderItem 생성 및 저장
        val orderItem = OrderItem(
            productId = 1L,
            productName = "테스트 상품",
            quantity = 2,
            price = 0L, // 초기 가격은 0 (상품 정보 응답에서 업데이트됨)
            orderId = savedOrder.id!!
        )
        orderItemRepository.save(orderItem)

        return savedOrder
    }
}