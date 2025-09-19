package hana.lovepet.paymentservice.integration

import com.fasterxml.jackson.databind.ObjectMapper
import hana.lovepet.paymentservice.api.payment.domain.Payment
import hana.lovepet.paymentservice.api.payment.domain.constant.PaymentStatus
import hana.lovepet.paymentservice.api.payment.repository.PaymentRepository
import hana.lovepet.paymentservice.infrastructure.kafka.Topics
import hana.lovepet.paymentservice.infrastructure.kafka.`in`.dto.PaymentCancelEvent
import hana.lovepet.paymentservice.infrastructure.kafka.`in`.dto.PaymentPendingEvent
import hana.lovepet.paymentservice.infrastructure.kafka.`in`.dto.PaymentPrepareEvent
import hana.lovepet.paymentservice.infrastructure.kafka.out.dto.*
import hana.lovepet.paymentservice.infrastructure.webclient.payment.TossClient
import hana.lovepet.paymentservice.infrastructure.webclient.payment.dto.response.TossPaymentCancelResponse
import hana.lovepet.paymentservice.infrastructure.webclient.payment.dto.response.TossPaymentConfirmResponse
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.mockito.kotlin.any
import org.mockito.kotlin.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
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
        Topics.PAYMENT_PREPARE,
        Topics.PAYMENT_PREPARE + "-dlt",
        Topics.PAYMENT_PREPARED,
        Topics.PAYMENT_PREPARED + "-dlt",
        Topics.PAYMENT_PREPARE_FAIL,
        Topics.PAYMENT_PREPARE_FAIL + "-dlt",
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
    ]
)
@ActiveProfiles("test")
class KafkaEventTest {

    @Autowired
    lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @Autowired
    lateinit var om: ObjectMapper

    @Autowired
    lateinit var paymentRepository: PaymentRepository

    @MockitoBean
    lateinit var tossClient: TossClient

    @Value("\${spring.embedded.kafka.brokers}")
    lateinit var brokers: String

    lateinit var consumer: Consumer<String, String>

    @BeforeEach
    fun setup() {
        val consumerProps = KafkaTestUtils.consumerProps(brokers, "test-payment-service", "true")
        consumerProps[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"

        val consumerFactory = DefaultKafkaConsumerFactory(
            consumerProps,
            StringDeserializer(),
            StringDeserializer()
        )

        consumer = consumerFactory.createConsumer()
        consumer.subscribe(
            listOf(
                Topics.PAYMENT_PREPARED, Topics.PAYMENT_PREPARED + "-dlt",
                Topics.PAYMENT_PREPARE_FAIL, Topics.PAYMENT_PREPARE_FAIL + "-dlt",
                Topics.PAYMENT_CONFIRMED, Topics.PAYMENT_CONFIRMED + "-dlt",
                Topics.PAYMENT_CONFIRMED_FAIL, Topics.PAYMENT_CONFIRMED_FAIL + "-dlt",
                Topics.PAYMENT_CANCELED, Topics.PAYMENT_CANCELED + "-dlt",
                Topics.PAYMENT_CANCELED_FAIL, Topics.PAYMENT_CANCELED_FAIL + "-dlt",
                Topics.PAYMENT_PREPARE, Topics.PAYMENT_PREPARE + "-dlt",
                Topics.PAYMENT_PENDING, Topics.PAYMENT_PENDING + "-dlt",
                Topics.PAYMENT_CANCEL, Topics.PAYMENT_CANCEL + "-dlt"
            )
        )

        paymentRepository.deleteAll()
    }

    @AfterEach
    fun tearDown() {
        consumer.close()
    }


    @Test
    @Timeout(10, unit = TimeUnit.SECONDS)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    fun `결제준비 요청 이벤트를 발행하면, 결제완료 응답 이벤트가 처리된다`() {
        //given
        val event = PaymentPrepareEvent(
            eventId = UUID.randomUUID().toString(),
            occurredAt = LocalDateTime.now().toString(),
            orderId = 1234L,
            userId = 5678L,
            amount = 50000L,
            method = "카드",
            idempotencyKey = "prepare-test-key"
        )
        val json = om.writeValueAsString(event)

        //when
        kafkaTemplate.send(Topics.PAYMENT_PREPARE, json)
        kafkaTemplate.flush()

        //then
        val record: ConsumerRecord<String, String> =
            KafkaTestUtils.getSingleRecord(consumer, Topics.PAYMENT_PREPARED, Duration.ofSeconds(5))

        val responseEvent = om.readValue(record.value(), PaymentPreparedEvent::class.java)

        assertThat(responseEvent.orderId).isEqualTo(event.orderId)
        assertThat(responseEvent.paymentId).isNotNull()
        assertThat(responseEvent.orderId.toString()).isEqualTo(event.orderId.toString())

        // 데이터베이스에 결제가 생성되었는지 확인
        val payment = paymentRepository.findByOrderId(event.orderId)
        assertThat(payment).isNotNull
        assertThat(payment!!.status).isEqualTo(PaymentStatus.PENDING)
        assertThat(payment.amount).isEqualTo(event.amount)
        assertThat(payment.userId).isEqualTo(event.userId)


    }


    @Test
    @Timeout(10, unit = TimeUnit.SECONDS)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    fun `결제준비 실패시 DLT 토픽으로 가고 실패메세지가 발행된다`() {
        //given
        val event = PaymentPrepareEvent(
            eventId = UUID.randomUUID().toString(),
            occurredAt = LocalDateTime.now().toString(),
            orderId = 9999L,
            userId = -1L, // 잘못된 userId
            amount = 50000L,
            method = "카드",
            idempotencyKey = "prepare-fail-key"
        )
        val json = om.writeValueAsString(event)

        //when
        kafkaTemplate.send(Topics.PAYMENT_PREPARE, event.orderId.toString(), json)
        kafkaTemplate.flush()

        //then
        // dlt 레코드 확인
        val dltRecord = KafkaTestUtils.getSingleRecord(consumer, Topics.PAYMENT_PREPARE + "-dlt", Duration.ofSeconds(5))
        val failedEvent = om.readValue(dltRecord.value(), PaymentPrepareEvent::class.java)

        assertThat(failedEvent).isNotNull
        assertThat(failedEvent.orderId).isEqualTo(9999L)

        // 실패 이벤트 확인
        val failRecord = KafkaTestUtils.getSingleRecord(consumer, Topics.PAYMENT_PREPARE_FAIL, Duration.ofSeconds(5))

        val failEvent = om.readValue(failRecord.value(), PaymentPrepareFailEvent::class.java)
        assertThat(failEvent.orderId).isEqualTo(event.orderId)
    }


    @Test
    @Timeout(10, unit = TimeUnit.SECONDS)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    fun `이미 결제준비가 되었다면, 아무런 메세지를 발행하지 않고 반환한다`() {
        //given
        val existingPayment = Payment(
            userId = 1111L,
            orderId = 5555L,
            paymentKey = "existing-payment-key",
            amount = 30000L,
            method = "카드",
            requestedAt = LocalDateTime.now()
        )
        paymentRepository.save(existingPayment)

        // 동일한 orderId로 결제 준비 요청
        val event = PaymentPrepareEvent(
            eventId = UUID.randomUUID().toString(),
            occurredAt = LocalDateTime.now().toString(),
            orderId = existingPayment.orderId,
            userId = 1111L,
            amount = 30000L,
            method = "카드",
            idempotencyKey = "duplicate-test-key"
        )
        val json = om.writeValueAsString(event)

        //when
        kafkaTemplate.send(Topics.PAYMENT_PREPARE, event.orderId.toString(), json)
        kafkaTemplate.flush()

        //then
        val result = assertThrows<IllegalStateException> {
            KafkaTestUtils.getSingleRecord(consumer, Topics.PAYMENT_PREPARED, Duration.ofSeconds(3))
        }
        assertThat(result.message).isEqualTo("No records found for topic") // 토픽 발행안됨

        // 데이터베이스에는 여전히 하나의 결제만 존재해야 함
        val payments = paymentRepository.findAll()
        assertThat(payments).hasSize(1)
        assertThat(payments[0].orderId).isEqualTo(5555L)
        assertThat(payments[0].paymentKey).isEqualTo("existing-payment-key") // 기존 결제 키 유지
    }


    @Test
    @Timeout(10, unit = TimeUnit.SECONDS)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    fun `결제확정 이벤트를 발행하면, 결제확정 이벤트가 처리된다`() {
        // given
        val payment = Payment(
            userId = 1111L,
            orderId = 5555L,
            paymentKey = "uuid-payment-key",
            amount = 30000L,
            method = "카드",
            requestedAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        val savedPayment = paymentRepository.save(payment).apply { id = 1L }



        val event = PaymentPendingEvent(
            eventId = UUID.randomUUID().toString(),
            orderId = savedPayment.orderId,
            paymentId = savedPayment.id!!,
            orderNo = "ORDER-${savedPayment.orderId}",
            paymentKey = "test-payment-key",
            amount = savedPayment.amount,
            idempotencyKey = "confirm-test-key"
        )

        given(tossClient.confirm(any())).willReturn(
            TossPaymentConfirmResponse(
                paymentKey = payment.paymentKey,
                orderId = event.orderNo,
                status = "DONE",
                totalAmount = payment.amount,
                method = payment.method,
                requestedAt = payment.requestedAt.toString(),
                approvedAt = LocalDateTime.now().toString(),
            )
        )

        val json = om.writeValueAsString(event)

        // when
        kafkaTemplate.send(Topics.PAYMENT_PENDING, event.orderId.toString(), json)
        kafkaTemplate.flush()

        // then
        val record: ConsumerRecord<String, String> =
            KafkaTestUtils.getSingleRecord(consumer, Topics.PAYMENT_CONFIRMED, Duration.ofSeconds(5))

        val responseEvent = om.readValue(record.value(), PaymentConfirmedEvent::class.java)

        assertThat(responseEvent.orderId).isEqualTo(event.orderId)
        assertThat(responseEvent.paymentId).isEqualTo(event.paymentId)

        val updatedPayment = paymentRepository.findById(savedPayment.id!!).orElseThrow()
        assertThat(updatedPayment.status).isEqualTo(PaymentStatus.SUCCESS)
    }



    @Test
    @Timeout(10, unit = TimeUnit.SECONDS)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    fun `결제확정 실패시 DLT 토픽으로 가고 실패 메세지가 발행된다`() {
        // given
        val payment = Payment(
            userId = 1111L,
            orderId = 5555L,
            paymentKey = "uuid-payment-key",
            amount = 30000L,
            method = "카드",
            requestedAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        val savedPayment = paymentRepository.save(payment).apply { id = 1L }


        val event = PaymentPendingEvent(
            eventId = UUID.randomUUID().toString(),
            orderId = payment.orderId,
            paymentId = payment.id!!,
            orderNo = "ORDER-9999",
            paymentKey = "invalid-payment-key",
            amount = 999L,
            idempotencyKey = "confirm-fail-key"
        )
        val json = om.writeValueAsString(event)

        // when
        kafkaTemplate.send(Topics.PAYMENT_PENDING, event.orderId.toString(), json)
        kafkaTemplate.flush()

        // then: DLT 토픽에서 메시지 확인
        val dltRecord = KafkaTestUtils.getSingleRecord(
            consumer,
            Topics.PAYMENT_PENDING + "-dlt",
            Duration.ofSeconds(5)
        )

        val failedEvent = om.readValue(dltRecord.value(), PaymentPendingEvent::class.java)
        assertThat(failedEvent).isNotNull
        assertThat(failedEvent.orderId).isEqualTo(payment.orderId)
        assertThat(failedEvent.paymentId).isEqualTo(payment.id!!)

        // 실패 이벤트도 발행되었는지 확인
        val failRecord = KafkaTestUtils.getSingleRecord(
            consumer,
            Topics.PAYMENT_CONFIRMED_FAIL,
            Duration.ofSeconds(5)
        )

        val failEvent = om.readValue(failRecord.value(), PaymentConfirmedFailEvent::class.java)
        assertThat(failEvent.orderId).isEqualTo(event.orderId)

        // 결제상태 변경 확인
        val updatedPayment = paymentRepository.findByOrderId(event.orderId)
        assertThat(updatedPayment).isNotNull
        assertThat(updatedPayment!!.status).isEqualTo(PaymentStatus.FAIL)
    }

    @Test
    @Timeout(10, unit = TimeUnit.SECONDS)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    fun `결제취소 요청 이벤트를 발행하면, 결제취소완료 응답 이벤트가 처리된다`() {
        // given
        val payment = Payment(
            userId = 1111L,
            orderId = 3456L,
            paymentKey = "success-payment-key",
            amount = 100000L,
            method = "카드",
            requestedAt = LocalDateTime.now()
        )
        // 결제를 SUCCESS 상태로 변경
        payment.status = PaymentStatus.SUCCESS
        val savedPayment = paymentRepository.save(payment)

        val event = PaymentCancelEvent(
            eventId = UUID.randomUUID().toString(),
            orderId = savedPayment.orderId,
            paymentId = savedPayment.id!!,
            orderNo = "ORDER-${savedPayment.orderId}",
            refundReason = "테스트 취소",
            idempotencyKey = "cancel-test-key"
        )

        // TossClient 취소 성공 응답 모킹
        given(tossClient.cancel(any(), any())).willReturn(
            TossPaymentCancelResponse(
                paymentKey = savedPayment.paymentKey,
                orderId = event.orderNo,
                status = "CANCELED",
                totalAmount = savedPayment.amount,
                balanceAmount = 0L,
                cancels = listOf(
                    TossPaymentCancelResponse.CancelDetail(
                        transactionKey = "test-transaction-key",
                        cancelReason = event.refundReason,
                        canceledAt = LocalDateTime.now().toString(),
                        cancelAmount = savedPayment.amount,
                        cancelStatus = "DONE"
                    )
                ),
                canceledAt = LocalDateTime.now().toString()
            )
        )

        val json = om.writeValueAsString(event)

        // when
        kafkaTemplate.send(Topics.PAYMENT_CANCEL, event.orderId.toString(), json)
        kafkaTemplate.flush()

        // then
        val record: ConsumerRecord<String, String> =
            KafkaTestUtils.getSingleRecord(consumer, Topics.PAYMENT_CANCELED, Duration.ofSeconds(5))

        val responseEvent = om.readValue(record.value(), PaymentCanceledEvent::class.java)

        assertThat(responseEvent.orderId).isEqualTo(event.orderId)
        assertThat(responseEvent.paymentId).isEqualTo(event.paymentId)

        // 결제 상태가 CANCELED로 변경되었는지 확인
        val updatedPayment = paymentRepository.findById(savedPayment.id!!).orElseThrow()
        assertThat(updatedPayment.status).isEqualTo(PaymentStatus.CANCELED)
    }

    @Test
    @Timeout(10, unit = TimeUnit.SECONDS)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    fun `결제취소 실패시 DLT 토픽으로 가고 실패 메세지가 발행된다`() {
        // given
        val payment = Payment(
            userId = 1111L,
            orderId = 8888L,
            paymentKey = "fail-cancel-payment-key",
            amount = 75000L,
            method = "카드",
            requestedAt = LocalDateTime.now()
        )
        payment.status = PaymentStatus.SUCCESS
        val savedPayment = paymentRepository.save(payment)

        val event = PaymentCancelEvent(
            eventId = UUID.randomUUID().toString(),
            orderId = savedPayment.orderId,
            paymentId = savedPayment.id!!,
            orderNo = "ORDER-${savedPayment.orderId}",
            refundReason = "실패 테스트 취소",
            idempotencyKey = "cancel-fail-key"
        )

        given(tossClient.cancel(any(), any())).willThrow(
            RuntimeException("토스 결제 취소 실패")
        )

        val json = om.writeValueAsString(event)

        // when
        kafkaTemplate.send(Topics.PAYMENT_CANCEL, event.orderId.toString(), json)
        kafkaTemplate.flush()

        // then
        val dltRecord = KafkaTestUtils.getSingleRecord(
            consumer,
            Topics.PAYMENT_CANCEL + "-dlt",
            Duration.ofSeconds(5)
        )

        val failedEvent = om.readValue(dltRecord.value(), PaymentCancelEvent::class.java)
        assertThat(failedEvent).isNotNull
        assertThat(failedEvent.orderId).isEqualTo(savedPayment.orderId)
        assertThat(failedEvent.paymentId).isEqualTo(savedPayment.id!!)

        // 실패 이벤트도 발행되었는지 확인
        val failRecord = KafkaTestUtils.getSingleRecord(
            consumer,
            Topics.PAYMENT_CANCELED_FAIL,
            Duration.ofSeconds(5)
        )

        val failEvent = om.readValue(failRecord.value(), PaymentCanceledFailEvent::class.java)
        assertThat(failEvent.orderId).isEqualTo(event.orderId)

        // 결제 상태는 여전히 SUCCESS여야 함 (취소 실패했으므로)
        val updatedPayment = paymentRepository.findById(savedPayment.id!!).orElseThrow()
        assertThat(updatedPayment.status).isEqualTo(PaymentStatus.SUCCESS)
    }

    @Test
    @Timeout(10, unit = TimeUnit.SECONDS)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    fun `이미 취소된 결제에 대해 취소 요청시 멱등 처리된다`() {
        // given: 이미 CANCELED 상태의 결제 생성
        val payment = Payment(
            userId = 1111L,
            orderId = 7777L,
            paymentKey = "canceled-payment-key",
            amount = 50000L,
            method = "카드",
            requestedAt = LocalDateTime.now()
        )
        // 결제를 CANCELED 상태로 변경
        payment.status = PaymentStatus.CANCELED
        payment.canceledAt = LocalDateTime.now()
        val savedPayment = paymentRepository.save(payment)

        val event = PaymentCancelEvent(
            eventId = UUID.randomUUID().toString(),
            orderId = savedPayment.orderId,
            paymentId = savedPayment.id!!,
            orderNo = "ORDER-${savedPayment.orderId}",
            refundReason = "중복 취소 테스트",
            idempotencyKey = "duplicate-cancel-key"
        )

        val json = om.writeValueAsString(event)

        // when: 이미 취소된 결제에 대해 다시 취소 요청
        kafkaTemplate.send(Topics.PAYMENT_CANCEL, event.orderId.toString(), json)
        kafkaTemplate.flush()

        // then: PAYMENT_CANCELED 이벤트가 발행되지 않아야 함 (멱등 처리)
        val result = assertThrows<IllegalStateException> {
            KafkaTestUtils.getSingleRecord(consumer, Topics.PAYMENT_CANCELED, Duration.ofSeconds(3))
        }
        assertThat(result.message).isEqualTo("No records found for topic") // 토픽 발행안됨


        // 결제 상태는 여전히 CANCELED 상태여야 함
        val updatedPayment = paymentRepository.findById(savedPayment.id!!).orElseThrow()
        assertThat(updatedPayment.status).isEqualTo(PaymentStatus.CANCELED)
    }
//
//    private fun createMockTossResponse(): hana.lovepet.paymentservice.infrastructure.webclient.payment.dto.response.TossPaymentConfirmResponse {
//        return hana.lovepet.paymentservice.infrastructure.webclient.payment.dto.response.TossPaymentConfirmResponse(
//            paymentKey = "test-payment-key",
//            orderId = "ORDER-2345",
//            status = "DONE",
//            totalAmount = 75000,
//            method = "카드",
//            requestedAt = "2023-01-01T00:00:00+09:00",
//            approvedAt = "2023-01-01T00:00:01+09:00"
//        )
//    }
//
//    private fun createMockTossCancelResponse(): hana.lovepet.paymentservice.infrastructure.webclient.payment.dto.response.TossPaymentCancelResponse {
//        return hana.lovepet.paymentservice.infrastructure.webclient.payment.dto.response.TossPaymentCancelResponse(
//            paymentKey = "test-payment-key",
//            orderId = "ORDER-3456",
//            status = "CANCELED",
//            totalAmount = 100000,
//            method = "카드",
//            requestedAt = "2023-01-01T00:00:00+09:00",
//            approvedAt = "2023-01-01T00:00:01+09:00",
//            cancels = listOf(
//                hana.lovepet.paymentservice.infrastructure.webclient.payment.dto.response.TossPaymentCancelResponse.CancelDetail(
//                    cancelAmount = 100000,
//                    cancelReason = "테스트 취소",
//                    canceledAt = "2023-01-01T00:00:02+09:00",
//                    cancelStatus = "DONE"
//                )
//            )
//        )
//    }
}
