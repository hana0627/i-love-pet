package hana.lovepet.paymentservice.api.payment.service

import hana.lovepet.paymentservice.common.exception.constant.ErrorCode
import hana.lovepet.paymentservice.api.payment.domain.Payment
import hana.lovepet.paymentservice.api.payment.domain.PaymentLog
import hana.lovepet.paymentservice.api.payment.domain.constant.LogType
import hana.lovepet.paymentservice.api.payment.domain.constant.PaymentStatus
import hana.lovepet.paymentservice.api.payment.repository.PaymentLogRepository
import hana.lovepet.paymentservice.api.payment.repository.PaymentRepository
import hana.lovepet.paymentservice.api.payment.service.impl.PaymentServiceImpl
import hana.lovepet.paymentservice.common.clock.TimeProvider
import hana.lovepet.paymentservice.common.exception.ApplicationException
import hana.lovepet.paymentservice.common.uuid.UUIDGenerator
import hana.lovepet.paymentservice.infrastructure.webclient.payment.TossClient
import hana.lovepet.paymentservice.infrastructure.kafka.`in`.dto.PaymentCancelEvent
import hana.lovepet.paymentservice.infrastructure.kafka.out.dto.PaymentCanceledEvent
import hana.lovepet.paymentservice.infrastructure.webclient.payment.dto.request.TossPaymentConfirmRequest
import hana.lovepet.paymentservice.infrastructure.webclient.payment.dto.response.TossPaymentCancelResponse
import hana.lovepet.paymentservice.infrastructure.webclient.payment.dto.response.TossPaymentConfirmResponse
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
import org.mockito.kotlin.times
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockitoExtension::class)
class PaymentServiceTest {

    @Mock
    lateinit var paymentRepository: PaymentRepository

    @Mock
    lateinit var paymentLogRepository: PaymentLogRepository

    @Mock
    lateinit var timeProvider: TimeProvider

    @Mock
    lateinit var uuidGenerator: UUIDGenerator

    @Mock
    lateinit var tossClient: TossClient

    @Mock
    lateinit var applicationEventPublisher: ApplicationEventPublisher

    lateinit var paymentService: PaymentService

    @BeforeEach
    fun setUp() {
        paymentService =
            PaymentServiceImpl(paymentRepository, paymentLogRepository, timeProvider, uuidGenerator, tossClient, applicationEventPublisher)
    }

    @Test
    fun `결제 준비에 성공한다`() {
        //given
        val userId = 1000L
        val orderId = 2000L
        val amount = 10000L
        val method = "카드"
        val paymentKey = "temp_pgid_UUID"
        val requestedAt = LocalDateTime.of(2025, 8, 3, 9, 0, 0)

        given(paymentRepository.findByOrderId(orderId)).willReturn(null)
        given(timeProvider.now()).willReturn(requestedAt)
        given(uuidGenerator.generate()).willReturn(paymentKey)

        given(paymentRepository.save(any())).willAnswer { invocation ->
            val savedPayment = invocation.arguments[0] as Payment
            ReflectionTestUtils.setField(savedPayment, "id", 1L)
            savedPayment
        }

        given(paymentLogRepository.save(any<PaymentLog>())).willAnswer { invocation ->
            val savedLog = invocation.arguments[0] as PaymentLog
            ReflectionTestUtils.setField(savedLog, "id", 1L)
            savedLog
        }

        //when
        val result = paymentService.preparePayment(userId, orderId, amount, method)

        //then
        then(paymentRepository).should().findByOrderId(orderId)
        then(paymentRepository).should().save(any<Payment>())
        then(paymentLogRepository).should().save(any<PaymentLog>())

        assertThat(result.paymentId).isEqualTo(1L)

    }

    @Test
    fun `이미 존재하는 orderId로 결제 준비시 기존 결제정보를 반환한다`() {
        //given
        val userId = 1000L
        val orderId = 2000L
        val amount = 10000L
        val method = "카드"
        val requestedAt = LocalDateTime.of(2025, 8, 3, 9, 0, 0)

        val existingPayment = Payment(
            userId = userId,
            orderId = orderId,
            paymentKey = "temp_pgid_UUID",
            amount = amount,
            method = method,
            requestedAt = requestedAt
        ).apply {
            ReflectionTestUtils.setField(this, "id", 1L)
        }

        given(paymentRepository.findByOrderId(orderId)).willReturn(existingPayment)

        //when
        val result = paymentService.preparePayment(userId, orderId, amount, method)

        //then
        then(paymentRepository).should().findByOrderId(orderId)
        then(paymentRepository).shouldHaveNoMoreInteractions()
        then(paymentLogRepository).shouldHaveNoInteractions()
        then(applicationEventPublisher).shouldHaveNoInteractions()

        assertThat(result.paymentId).isEqualTo(existingPayment.id!!)
    }

    @Test
    fun `결제 확정에 성공한다`() {
        //given
        val orderId = 1000L
        val paymentId = 1L
        val orderNo = "order-123"
        val paymentKey = "payment-key-123"
        val amount = 10000L
        val requestedAt = LocalDateTime.of(2025, 8, 3, 9, 0, 0)
        val approvedAt = LocalDateTime.of(2025, 8, 3, 9, 5, 0)

        val payment = Payment(
            userId = 2000L,
            orderId = orderId,
            paymentKey = "temp_pgid_UUID",
            amount = amount,
            method = "카드",
            requestedAt = requestedAt
        ).apply {
            ReflectionTestUtils.setField(this, "id", paymentId)
        }

        val tossResponse = TossPaymentConfirmResponse(
            paymentKey = paymentKey,
            orderId = orderNo,
            status = "DONE",
            totalAmount = amount,
            method = "카드",
            requestedAt = requestedAt.toString(),
            approvedAt = approvedAt.toString()
        )

        given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment))
        given(timeProvider.now()).willReturn(approvedAt)
        given(uuidGenerator.generate()).willReturn("temp_pgid_UUID")
        given(tossClient.confirm(any<TossPaymentConfirmRequest>())).willReturn(tossResponse)
        given(paymentRepository.save(any<Payment>())).willAnswer { it.arguments[0] as Payment }
        given(paymentLogRepository.save(any<PaymentLog>())).willAnswer { it.arguments[0] as PaymentLog }

        //when
        val result = paymentService.confirmPayment(orderId, paymentId, orderNo, paymentKey, amount)

        //then
        then(tossClient).should().confirm(any())
        then(paymentRepository).should().save(any<Payment>())
        then(paymentLogRepository).should().save(any<PaymentLog>())

        assertThat(result.paymentId).isEqualTo(paymentId)
    }

    @Test
    fun `존재하지 않는 paymentId로 결제 확정시 예외가 발생한다`() {
        //given
        val orderId = 1000L
        val paymentId = 999L
        val orderNo = "order-123"
        val paymentKey = "payment-key-123"
        val amount = 10000L

        given(paymentRepository.findById(paymentId)).willReturn(Optional.empty())

        //when & then
        val result = assertThrows<ApplicationException> {
            paymentService.confirmPayment(orderId, paymentId, orderNo, paymentKey, amount)
        }

        then(paymentRepository).should().findById(paymentId)
        then(paymentLogRepository).shouldHaveNoInteractions()
        then(applicationEventPublisher).shouldHaveNoInteractions()
        then(tossClient).shouldHaveNoInteractions()

        assertThat(result.errorCode).isEqualTo(ErrorCode.PAYMENT_NOT_FOUND)
    }

    @Test
    fun `임시 실패 케이스 - 999원 주문시 결제확정에 실패한다`() {
        //given
        val orderId = 1000L
        val paymentId = 1L
        val orderNo = "order-123"
        val paymentKey = "payment-key-123"
        val amount = 999L
        val requestedAt = LocalDateTime.of(2025, 8, 3, 9, 0, 0)

        val payment = Payment(
            userId = 2000L,
            orderId = orderId,
            paymentKey = "temp_pgid_UUID",
            amount = amount,
            method = "카드",
            requestedAt = requestedAt
        ).apply {
            ReflectionTestUtils.setField(this, "id", paymentId)
        }

        given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment))

        //when & then
        val result = assertThrows<ApplicationException> {
            paymentService.confirmPayment(orderId, paymentId, orderNo, paymentKey, amount)
        }

        then(paymentRepository).should().findById(paymentId)
        then(paymentLogRepository).shouldHaveNoInteractions()
        then(applicationEventPublisher).shouldHaveNoInteractions()

        assertThat(result.errorCode).isEqualTo(ErrorCode.UNHEALTHY_SERVER_COMMUNICATION)
        assertThat(result.message).isEqualTo("테스트용 결제 준비 실패 -- PG결제이전")
    }

    @Test
    fun `임시 실패 케이스 - 888원 주문시 결제확정에 실패한다`() {
        //given
        val orderId = 1000L
        val paymentId = 1L
        val orderNo = "order-123"
        val paymentKey = "payment-key-123"
        val amount = 888L
        val requestedAt = LocalDateTime.of(2025, 8, 3, 9, 0, 0)
        val approvedAt = LocalDateTime.of(2025, 8, 3, 9, 5, 0)

        val payment = Payment(
            userId = 2000L,
            orderId = orderId,
            paymentKey = "temp_pgid_UUID",
            amount = amount,
            method = "카드",
            requestedAt = requestedAt
        ).apply {
            ReflectionTestUtils.setField(this, "id", paymentId)
        }

        val tossResponse = TossPaymentConfirmResponse(
            paymentKey = paymentKey,
            orderId = orderNo,
            status = "DONE",
            totalAmount = amount,
            method = "카드",
            requestedAt = requestedAt.toString(),
            approvedAt = approvedAt.toString()
        )

        given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment))
        given(timeProvider.now()).willReturn(approvedAt)
        given(tossClient.confirm(any<TossPaymentConfirmRequest>())).willReturn(tossResponse)
        given(paymentRepository.save(any<Payment>())).willAnswer { it.arguments[0] as Payment }
        given(paymentLogRepository.save(any<PaymentLog>())).willAnswer { it.arguments[0] as PaymentLog }

        //when & then
        val exception = assertThrows<ApplicationException> {
            paymentService.confirmPayment(orderId, paymentId, orderNo, paymentKey, amount)
        }

        then(paymentRepository).should().save(any<Payment>())
        then(paymentLogRepository).should().save(any<PaymentLog>())
        then(tossClient).should().confirm(any())
        then(applicationEventPublisher).shouldHaveNoInteractions()


        assertThat(exception.errorCode).isEqualTo(ErrorCode.UNHEALTHY_SERVER_COMMUNICATION)
        assertThat(exception.message).isEqualTo("테스트용 결제 준비 실패 -- PG결제이후")
    }

    @Test
    fun `TossClient 통신 실패시 예외가 발생하고 결제가 실패 처리된다`() {
        //given
        val orderId = 1000L
        val paymentId = 1L
        val orderNo = "order-123"
        val paymentKey = "payment-key-123"
        val amount = 10000L
        val requestedAt = LocalDateTime.of(2025, 8, 3, 9, 0, 0)
        val failedAt = LocalDateTime.of(2025, 8, 3, 9, 5, 0)

        val payment = Payment(
            userId = 2000L,
            orderId = orderId,
            paymentKey = "temp_pgid_UUID",
            amount = amount,
            method = "카드",
            requestedAt = requestedAt
        ).apply {
            ReflectionTestUtils.setField(this, "id", paymentId)
        }

        val tossException = ApplicationException(ErrorCode.UNHEALTHY_PG_COMMUNICATION, "토스 통신 실패")

        given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment))
        given(timeProvider.now()).willReturn(failedAt)
        given(tossClient.confirm(any<TossPaymentConfirmRequest>())).willThrow(tossException)
        given(paymentRepository.save(any<Payment>())).willAnswer { it.arguments[0] as Payment }
        given(paymentLogRepository.save(any<PaymentLog>())).willAnswer { it.arguments[0] as PaymentLog }

        //when & then
        val result = assertThrows<ApplicationException> {
            paymentService.confirmPayment(orderId, paymentId, orderNo, paymentKey, amount)
        }

        then(paymentRepository).should().save(any<Payment>())
        then(paymentLogRepository).should().save(any<PaymentLog>())
        then(tossClient).should().confirm(any())
        then(applicationEventPublisher).shouldHaveNoInteractions()

        assertThat(result.errorCode).isEqualTo(ErrorCode.UNHEALTHY_PG_COMMUNICATION)
    }

    @Test
    fun `Toss 응답 상태가 DONE이 아니면 예외가 발생한다`() {
        //given
        val orderId = 1000L
        val paymentId = 1L
        val orderNo = "order-123"
        val paymentKey = "payment-key-123"
        val amount = 10000L
        val requestedAt = LocalDateTime.of(2025, 8, 3, 9, 0, 0)
        val failedAt = LocalDateTime.of(2025, 8, 3, 9, 5, 0)

        val payment = Payment(
            userId = 2000L,
            orderId = orderId,
            paymentKey = "temp_pgid_UUID",
            amount = amount,
            method = "카드",
            requestedAt = requestedAt
        ).apply {
            ReflectionTestUtils.setField(this, "id", paymentId)
        }

        val tossResponse = TossPaymentConfirmResponse(
            paymentKey = paymentKey,
            orderId = orderNo,
            status = "FAILED",
            totalAmount = amount,
            method = "카드",
            requestedAt = requestedAt.toString(),
            approvedAt = null
        )

        given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment))
        given(timeProvider.now()).willReturn(failedAt)
        given(tossClient.confirm(any<TossPaymentConfirmRequest>())).willReturn(tossResponse)
        given(paymentRepository.save(any<Payment>())).willAnswer { it.arguments[0] as Payment }
        given(paymentLogRepository.save(any<PaymentLog>())).willAnswer { it.arguments[0] as PaymentLog }

        //when & then
        val result = assertThrows<ApplicationException> {
            paymentService.confirmPayment(orderId, paymentId, orderNo, paymentKey, amount)
        }

        then(paymentRepository).should().save(any<Payment>())
        then(paymentLogRepository).should().save(any<PaymentLog>())
        then(tossClient).should().confirm(any())
        then(applicationEventPublisher).shouldHaveNoInteractions()


        assertThat(result.errorCode).isEqualTo(ErrorCode.UNHEALTHY_PG_COMMUNICATION)
        assertThat(result.message).contains("결제 승인 상태 오류: FAILED")
    }

    @Test
    fun `결제 금액이 일치하지 않을 때 예외가 발생한다`() {
        //given
        val orderId = 1000L
        val paymentId = 1L
        val orderNo = "order-123"
        val paymentKey = "payment-key-123"
        val amount = 10000L
        val wrongAmount = 5000L
        val requestedAt = LocalDateTime.of(2025, 8, 3, 9, 0, 0)
        val failedAt = LocalDateTime.of(2025, 8, 3, 9, 5, 0)

        val payment = Payment(
            userId = 2000L,
            orderId = orderId,
            paymentKey = "temp_pgid_UUID",
            amount = amount,
            method = "카드",
            requestedAt = requestedAt
        ).apply {
            ReflectionTestUtils.setField(this, "id", paymentId)
        }

        val tossResponse = TossPaymentConfirmResponse(
            paymentKey = paymentKey,
            orderId = orderNo,
            status = "DONE",
            totalAmount = wrongAmount,
            method = "카드",
            requestedAt = requestedAt.toString(),
            approvedAt = failedAt.toString()
        )

        given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment))
        given(timeProvider.now()).willReturn(failedAt)
        given(tossClient.confirm(any<TossPaymentConfirmRequest>())).willReturn(tossResponse)
        given(paymentRepository.save(any<Payment>())).willAnswer { it.arguments[0] as Payment }
        given(paymentLogRepository.save(any<PaymentLog>())).willAnswer { it.arguments[0] as PaymentLog }

        //when & then
        val result = assertThrows<ApplicationException> {
            paymentService.confirmPayment(orderId, paymentId, orderNo, paymentKey, amount)
        }

        then(paymentRepository).should().save(any<Payment>())
        then(paymentLogRepository).should().save(any<PaymentLog>())
        then(tossClient).should().confirm(any())
        then(applicationEventPublisher).shouldHaveNoInteractions()


        assertThat(result.errorCode).isEqualTo(ErrorCode.UNHEALTHY_PG_COMMUNICATION)
        assertThat(result.message).contains("결제 금액 불일치: expected=${amount}, actual=${wrongAmount}")
    }

    @Test
    fun `결제 취소에 성공한다`() {
        //given
        val orderId = 1000L
        val paymentId = 1L
        val refundReason = "재고차감 실패"
        val paymentKey = "payment-key-123"
        val canceledAt = LocalDateTime.of(2025, 8, 3, 9, 10, 0)
        val transactionKey = "transaction-key-123"
        val cancelAmount = 10000L

        val paymentCancelEvent = PaymentCancelEvent(
            eventId = "event-123",
            orderId = orderId,
            orderNo = "order-123",
            paymentId = paymentId,
            refundReason = refundReason,
            idempotencyKey = "idempotency-123"
        )

        val payment = Payment(
            userId = 2000L,
            orderId = orderId,
            paymentKey = paymentKey,
            amount = 10000L,
            method = "카드",
            requestedAt = LocalDateTime.of(2025, 8, 3, 9, 0, 0)
        ).apply {
            ReflectionTestUtils.setField(this, "id", paymentId)
            ReflectionTestUtils.setField(this, "status", PaymentStatus.SUCCESS)
        }

        val cancelDetail = TossPaymentCancelResponse.CancelDetail(
            transactionKey = transactionKey,
            cancelReason = refundReason,
            canceledAt = canceledAt.toString(),
            cancelAmount = cancelAmount,
            cancelStatus = "DONE"
        )

        val tossResponse = TossPaymentCancelResponse(
            paymentKey = paymentKey,
            orderId = "order-123",
            status = "CANCELED",
            totalAmount = 10000L,
            balanceAmount = 0L,
            cancels = listOf(cancelDetail),
            canceledAt = canceledAt.toString()
        )

        given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment))
        given(timeProvider.now()).willReturn(canceledAt)
        given(uuidGenerator.generate()).willReturn("event-id-123")
        given(tossClient.cancel(paymentKey, refundReason)).willReturn(tossResponse)
        given(paymentRepository.save(any<Payment>())).willAnswer { it.arguments[0] as Payment }
        given(paymentLogRepository.save(any<PaymentLog>())).willAnswer { it.arguments[0] as PaymentLog }

        //when
        val result = paymentService.cancelPayment(paymentCancelEvent)

        //then
        then(paymentRepository).should().findById(paymentId)
        then(paymentLogRepository).should(times(2)).save(any<PaymentLog>())
        then(tossClient).should().cancel(paymentKey, refundReason)
        then(paymentRepository).should().save(any<Payment>())
        then(applicationEventPublisher).should().publishEvent(any<PaymentCanceledEvent>())

        assertThat(result.paymentId).isEqualTo(paymentId)
        assertThat(result.transactionKey).isEqualTo(transactionKey)
        assertThat(result.message).isEqualTo("결제 취소 완료")
    }

    @Test
    fun `존재하지 않는 paymentId로 결제 취소시 예외가 발생한다`() {
        //given
        val paymentId = 999L
        val paymentCancelEvent = PaymentCancelEvent(
            eventId = "event-123",
            orderId = 1000L,
            orderNo = "order-123",
            paymentId = paymentId,
            refundReason = "재고차감 실패",
            idempotencyKey = "idempotency-123"
        )

        given(paymentRepository.findById(paymentId)).willReturn(Optional.empty())

        //when & then
        val result = assertThrows<ApplicationException> {
            paymentService.cancelPayment(paymentCancelEvent)
        }

        then(paymentRepository).should().findById(paymentId)
        then(paymentLogRepository).shouldHaveNoInteractions()
        then(tossClient).shouldHaveNoInteractions()
        then(applicationEventPublisher).shouldHaveNoInteractions()

        assertThat(result.errorCode).isEqualTo(ErrorCode.PAYMENT_NOT_FOUND)
        assertThat(result.message).isEqualTo("Payments not found [id = $paymentId]")
    }

    @Test
    fun `이미 취소된 결제는 멱등 처리로 성공 응답을 반환한다`() {
        //given
        val orderId = 1000L
        val paymentId = 1L
        val canceledAt = LocalDateTime.of(2025, 8, 3, 9, 10, 0)

        val paymentCancelEvent = PaymentCancelEvent(
            eventId = "event-123",
            orderId = orderId,
            orderNo = "order-123",
            paymentId = paymentId,
            refundReason = "재고차감 실패",
            idempotencyKey = "idempotency-123"
        )

        val payment = Payment(
            userId = 2000L,
            orderId = orderId,
            paymentKey = "payment-key-123",
            amount = 10000L,
            method = "카드",
            requestedAt = LocalDateTime.of(2025, 8, 3, 9, 0, 0)
        ).apply {
            ReflectionTestUtils.setField(this, "id", paymentId)
            ReflectionTestUtils.setField(this, "status", PaymentStatus.CANCELED)
            ReflectionTestUtils.setField(this, "canceledAt", canceledAt)
        }

        given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment))

        //when
        val result = paymentService.cancelPayment(paymentCancelEvent)

        //then
        then(paymentRepository).should().findById(paymentId)
        then(paymentLogRepository).shouldHaveNoInteractions()
        then(tossClient).shouldHaveNoInteractions()
        then(applicationEventPublisher).shouldHaveNoInteractions()

        assertThat(result.paymentId).isEqualTo(paymentId)
        assertThat(result.canceledAt).isEqualTo(canceledAt)
        assertThat(result.transactionKey).isNull()
        assertThat(result.message).isEqualTo("이미 취소된 결제입니다.")
    }

    @Test
    fun `Toss 취소 응답 상태가 CANCELED가 아닐 때 예외가 발생한다`() {
        //given
        val orderId = 1000L
        val paymentId = 1L
        val refundReason = "재고차감 실패"
        val paymentKey = "payment-key-123"
        val failedAt = LocalDateTime.of(2025, 8, 3, 9, 10, 0)

        val paymentCancelEvent = PaymentCancelEvent(
            eventId = "event-123",
            orderId = orderId,
            orderNo = "order-123",
            paymentId = paymentId,
            refundReason = refundReason,
            idempotencyKey = "idempotency-123"
        )

        val payment = Payment(
            userId = 2000L,
            orderId = orderId,
            paymentKey = paymentKey,
            amount = 10000L,
            method = "카드",
            requestedAt = LocalDateTime.of(2025, 8, 3, 9, 0, 0)
        ).apply {
            ReflectionTestUtils.setField(this, "id", paymentId)
            ReflectionTestUtils.setField(this, "status", PaymentStatus.SUCCESS)
        }

        val tossResponse = TossPaymentCancelResponse(
            paymentKey = paymentKey,
            orderId = "order-123",
            status = "FAILED",
            totalAmount = 10000L,
            balanceAmount = 10000L,
            cancels = emptyList(),
            canceledAt = null
        )

        given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment))
        given(tossClient.cancel(paymentKey, refundReason)).willReturn(tossResponse)
        given(paymentLogRepository.save(any<PaymentLog>())).willAnswer { it.arguments[0] as PaymentLog }

        //when & then
        val result = assertThrows<ApplicationException> {
            paymentService.cancelPayment(paymentCancelEvent)
        }

        then(paymentRepository).should().findById(paymentId)
        then(paymentLogRepository).should(times(3)).save(any<PaymentLog>())
        then(tossClient).should().cancel(paymentKey, refundReason)
        then(applicationEventPublisher).shouldHaveNoInteractions()

        assertThat(result.errorCode).isEqualTo(ErrorCode.UNHEALTHY_PG_COMMUNICATION)
        assertThat(result.message).contains("토스페이먼츠 취소 상태 오류: FAILED")
    }

    @Test
    fun `취소 상세 정보가 없을 때 예외가 발생한다`() {
        //given
        val orderId = 1000L
        val paymentId = 1L
        val refundReason = "재고차감 실패"
        val paymentKey = "payment-key-123"
        val failedAt = LocalDateTime.of(2025, 8, 3, 9, 10, 0)

        val paymentCancelEvent = PaymentCancelEvent(
            eventId = "event-123",
            orderId = orderId,
            orderNo = "order-123",
            paymentId = paymentId,
            refundReason = refundReason,
            idempotencyKey = "idempotency-123"
        )

        val payment = Payment(
            userId = 2000L,
            orderId = orderId,
            paymentKey = paymentKey,
            amount = 10000L,
            method = "카드",
            requestedAt = LocalDateTime.of(2025, 8, 3, 9, 0, 0)
        ).apply {
            ReflectionTestUtils.setField(this, "id", paymentId)
            ReflectionTestUtils.setField(this, "status", PaymentStatus.SUCCESS)
        }

        val tossResponse = TossPaymentCancelResponse(
            paymentKey = paymentKey,
            orderId = "order-123",
            status = "CANCELED",
            totalAmount = 10000L,
            balanceAmount = 0L,
            cancels = emptyList(),
            canceledAt = failedAt.toString()
        )

        given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment))
        given(tossClient.cancel(paymentKey, refundReason)).willReturn(tossResponse)
        given(paymentLogRepository.save(any<PaymentLog>())).willAnswer { it.arguments[0] as PaymentLog }

        //when & then
        val result = assertThrows<ApplicationException> {
            paymentService.cancelPayment(paymentCancelEvent)
        }

        then(paymentRepository).should().findById(paymentId)
        then(paymentLogRepository).should(times(2)).save(any<PaymentLog>())
        then(tossClient).should().cancel(paymentKey, refundReason)
        then(applicationEventPublisher).shouldHaveNoInteractions()

        assertThat(result.errorCode).isEqualTo(ErrorCode.UNHEALTHY_PG_COMMUNICATION)
        assertThat(result.message).isEqualTo("취소 상세 정보를 찾을 수 없습니다.")
    }

    @Test
    fun `취소 상세 정보의 cancelStatus가 DONE이 아닐 때 예외가 발생한다`() {
        //given
        val orderId = 1000L
        val paymentId = 1L
        val refundReason = "재고차감 실패"
        val paymentKey = "payment-key-123"
        val failedAt = LocalDateTime.of(2025, 8, 3, 9, 10, 0)

        val paymentCancelEvent = PaymentCancelEvent(
            eventId = "event-123",
            orderId = orderId,
            orderNo = "order-123",
            paymentId = paymentId,
            refundReason = refundReason,
            idempotencyKey = "idempotency-123"
        )

        val payment = Payment(
            userId = 2000L,
            orderId = orderId,
            paymentKey = paymentKey,
            amount = 10000L,
            method = "카드",
            requestedAt = LocalDateTime.of(2025, 8, 3, 9, 0, 0)
        ).apply {
            ReflectionTestUtils.setField(this, "id", paymentId)
            ReflectionTestUtils.setField(this, "status", PaymentStatus.SUCCESS)
        }

        val cancelDetail = TossPaymentCancelResponse.CancelDetail(
            transactionKey = "transaction-key-123",
            cancelReason = refundReason,
            canceledAt = failedAt.toString(),
            cancelAmount = 10000L,
            cancelStatus = "PENDING"
        )

        val tossResponse = TossPaymentCancelResponse(
            paymentKey = paymentKey,
            orderId = "order-123",
            status = "CANCELED",
            totalAmount = 10000L,
            balanceAmount = 0L,
            cancels = listOf(cancelDetail),
            canceledAt = failedAt.toString()
        )

        given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment))
        given(tossClient.cancel(paymentKey, refundReason)).willReturn(tossResponse)
        given(paymentLogRepository.save(any<PaymentLog>())).willAnswer { it.arguments[0] as PaymentLog }

        //when & then
        val result = assertThrows<ApplicationException> {
            paymentService.cancelPayment(paymentCancelEvent)
        }

        then(paymentRepository).should().findById(paymentId)
        then(paymentLogRepository).should(times(3)).save(any<PaymentLog>())
        then(tossClient).should().cancel(paymentKey, refundReason)
        then(applicationEventPublisher).shouldHaveNoInteractions()

        assertThat(result.errorCode).isEqualTo(ErrorCode.UNHEALTHY_PG_COMMUNICATION)
        assertThat(result.message).contains("취소 처리 상태 오류: PENDING")
    }

    @Test
    fun `TossClient 취소 중 ApplicationException 발생시 예외가 전파된다`() {
        //given
        val orderId = 1000L
        val paymentId = 1L
        val refundReason = "재고차감 실패"
        val paymentKey = "payment-key-123"
        val failedAt = LocalDateTime.of(2025, 8, 3, 9, 10, 0)

        val paymentCancelEvent = PaymentCancelEvent(
            eventId = "event-123",
            orderId = orderId,
            orderNo = "order-123",
            paymentId = paymentId,
            refundReason = refundReason,
            idempotencyKey = "idempotency-123"
        )

        val payment = Payment(
            userId = 2000L,
            orderId = orderId,
            paymentKey = paymentKey,
            amount = 10000L,
            method = "카드",
            requestedAt = LocalDateTime.of(2025, 8, 3, 9, 0, 0)
        ).apply {
            ReflectionTestUtils.setField(this, "id", paymentId)
            ReflectionTestUtils.setField(this, "status", PaymentStatus.SUCCESS)
        }

        val tossException = ApplicationException(ErrorCode.UNHEALTHY_PG_COMMUNICATION, "토스 취소 통신 실패")

        given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment))
        given(tossClient.cancel(paymentKey, refundReason)).willThrow(tossException)
        given(paymentLogRepository.save(any<PaymentLog>())).willAnswer { it.arguments[0] as PaymentLog }

        //when & then
        val result = assertThrows<ApplicationException> {
            paymentService.cancelPayment(paymentCancelEvent)
        }

        then(paymentRepository).should().findById(paymentId)
        then(paymentLogRepository).should(times(2)).save(any<PaymentLog>())
        then(tossClient).should().cancel(paymentKey, refundReason)
        then(applicationEventPublisher).shouldHaveNoInteractions()

        assertThat(result.errorCode).isEqualTo(ErrorCode.UNHEALTHY_PG_COMMUNICATION)
        assertThat(result.message).isEqualTo("토스 취소 통신 실패")
    }

    @Test
    fun `TossClient 예상하지 못한 예외 발생시 ApplicationException으로 변환된다`() {
        //given
        val orderId = 1000L
        val paymentId = 1L
        val refundReason = "재고차감 실패"
        val paymentKey = "payment-key-123"
        val failedAt = LocalDateTime.of(2025, 8, 3, 9, 10, 0)

        val paymentCancelEvent = PaymentCancelEvent(
            eventId = "event-123",
            orderId = orderId,
            orderNo = "order-123",
            paymentId = paymentId,
            refundReason = refundReason,
            idempotencyKey = "idempotency-123"
        )

        val payment = Payment(
            userId = 2000L,
            orderId = orderId,
            paymentKey = paymentKey,
            amount = 10000L,
            method = "카드",
            requestedAt = LocalDateTime.of(2025, 8, 3, 9, 0, 0)
        ).apply {
            ReflectionTestUtils.setField(this, "id", paymentId)
            ReflectionTestUtils.setField(this, "status", PaymentStatus.SUCCESS)
        }

        val genericException = RuntimeException("네트워크 연결 오류")

        given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment))
        given(tossClient.cancel(paymentKey, refundReason)).willThrow(genericException)
        given(paymentLogRepository.save(any<PaymentLog>())).willAnswer { it.arguments[0] as PaymentLog }

        //when & then
        val result = assertThrows<ApplicationException> {
            paymentService.cancelPayment(paymentCancelEvent)
        }

        then(paymentRepository).should().findById(paymentId)
        then(paymentLogRepository).should(times(2)).save(any<PaymentLog>())
        then(tossClient).should().cancel(paymentKey, refundReason)
        then(applicationEventPublisher).shouldHaveNoInteractions()

        assertThat(result.errorCode).isEqualTo(ErrorCode.UNHEALTHY_PG_COMMUNICATION)
        assertThat(result.message).isEqualTo("결제 취소 중 오류가 발생했습니다.")
    }

    @Test
    fun `존재하지 않는 paymentId로 결제 조회시 예외가 발생한다`() {
        //given
        val paymentId = 999L

        given(paymentRepository.findById(paymentId)).willReturn(Optional.empty())

        //when & then
        val result = assertThrows<ApplicationException> {
            paymentService.getPayment(paymentId)
        }

        then(paymentRepository).should().findById(paymentId)

        assertThat(result.errorCode).isEqualTo(ErrorCode.PAYMENT_NOT_FOUND)
        assertThat(result.message).isEqualTo("Payments not found [id = $paymentId]")
    }

    @Test
    fun `PENDING 상태의 결제 조회에 성공한다`() {
        //given
        val paymentId = 1L
        val requestedAt = LocalDateTime.of(2025, 8, 3, 9, 0, 0)

        val payment = Payment(
            userId = 2000L,
            orderId = 1000L,
            paymentKey = "payment-key-123",
            amount = 10000L,
            method = "카드",
            requestedAt = requestedAt
        ).apply {
            ReflectionTestUtils.setField(this, "id", paymentId)
            ReflectionTestUtils.setField(this, "status", PaymentStatus.PENDING)
            ReflectionTestUtils.setField(this, "description", "주문 설명")
        }

        given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment))

        //when
        val result = paymentService.getPayment(paymentId)

        //then
        then(paymentRepository).should().findById(paymentId)

        assertThat(result.paymentId).isEqualTo(paymentId)
        assertThat(result.status).isEqualTo(PaymentStatus.PENDING)
        assertThat(result.amount).isEqualTo(10000L)
        assertThat(result.method).isEqualTo("카드")
        assertThat(result.occurredAt).isEqualTo(requestedAt)
        assertThat(result.description).isEqualTo("주문 설명")
    }

    @Test
    fun `SUCCESS 상태의 결제 조회에 성공한다`() {
        //given
        val paymentId = 1L
        val requestedAt = LocalDateTime.of(2025, 8, 3, 9, 0, 0)
        val approvedAt = LocalDateTime.of(2025, 8, 3, 9, 5, 0)

        val payment = Payment(
            userId = 2000L,
            orderId = 1000L,
            paymentKey = "payment-key-123",
            amount = 10000L,
            method = "카드",
            requestedAt = requestedAt
        ).apply {
            ReflectionTestUtils.setField(this, "id", paymentId)
            ReflectionTestUtils.setField(this, "status", PaymentStatus.SUCCESS)
            ReflectionTestUtils.setField(this, "approvedAt", approvedAt)
            ReflectionTestUtils.setField(this, "description", "주문 설명")
        }

        given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment))

        //when
        val result = paymentService.getPayment(paymentId)

        //then
        then(paymentRepository).should().findById(paymentId)

        assertThat(result.paymentId).isEqualTo(paymentId)
        assertThat(result.status).isEqualTo(PaymentStatus.SUCCESS)
        assertThat(result.amount).isEqualTo(10000L)
        assertThat(result.method).isEqualTo("카드")
        assertThat(result.occurredAt).isEqualTo(approvedAt)
        assertThat(result.description).isEqualTo("주문 설명")
    }

    @Test
    fun `FAIL 상태의 결제 조회에 성공한다`() {
        //given
        val paymentId = 1L
        val requestedAt = LocalDateTime.of(2025, 8, 3, 9, 0, 0)
        val failedAt = LocalDateTime.of(2025, 8, 3, 9, 3, 0)
        val failReason = "잔액 부족"

        val payment = Payment(
            userId = 2000L,
            orderId = 1000L,
            paymentKey = "payment-key-123",
            amount = 10000L,
            method = "카드",
            requestedAt = requestedAt
        ).apply {
            ReflectionTestUtils.setField(this, "id", paymentId)
            ReflectionTestUtils.setField(this, "status", PaymentStatus.FAIL)
            ReflectionTestUtils.setField(this, "failedAt", failedAt)
            ReflectionTestUtils.setField(this, "failReason", failReason)
        }

        given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment))

        //when
        val result = paymentService.getPayment(paymentId)

        //then
        then(paymentRepository).should().findById(paymentId)

        assertThat(result.paymentId).isEqualTo(paymentId)
        assertThat(result.status).isEqualTo(PaymentStatus.FAIL)
        assertThat(result.amount).isEqualTo(10000L)
        assertThat(result.method).isEqualTo("카드")
        assertThat(result.occurredAt).isEqualTo(failedAt)
        assertThat(result.description).isEqualTo(failReason)
    }

    @Test
    fun `CANCELED 상태의 결제 조회에 성공한다`() {
        //given
        val paymentId = 1L
        val requestedAt = LocalDateTime.of(2025, 8, 3, 9, 0, 0)
        val canceledAt = LocalDateTime.of(2025, 8, 3, 9, 10, 0)

        val payment = Payment(
            userId = 2000L,
            orderId = 1000L,
            paymentKey = "payment-key-123",
            amount = 10000L,
            method = "카드",
            requestedAt = requestedAt
        ).apply {
            ReflectionTestUtils.setField(this, "id", paymentId)
            ReflectionTestUtils.setField(this, "status", PaymentStatus.CANCELED)
            ReflectionTestUtils.setField(this, "canceledAt", canceledAt)
            ReflectionTestUtils.setField(this, "description", "재고차감 실패")
        }

        given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment))

        //when
        val result = paymentService.getPayment(paymentId)

        //then
        then(paymentRepository).should().findById(paymentId)

        assertThat(result.paymentId).isEqualTo(paymentId)
        assertThat(result.status).isEqualTo(PaymentStatus.CANCELED)
        assertThat(result.amount).isEqualTo(10000L)
        assertThat(result.method).isEqualTo("카드")
        assertThat(result.occurredAt).isEqualTo(canceledAt)
        assertThat(result.description).isEqualTo("재고차감 실패")
    }

    @Test
    fun `REFUNDED 상태의 결제 조회에 성공한다`() {
        //given
        val paymentId = 1L
        val requestedAt = LocalDateTime.of(2025, 8, 3, 9, 0, 0)
        val refundedAt = LocalDateTime.of(2025, 8, 3, 9, 15, 0)

        val payment = Payment(
            userId = 2000L,
            orderId = 1000L,
            paymentKey = "payment-key-123",
            amount = 10000L,
            method = "카드",
            requestedAt = requestedAt
        ).apply {
            ReflectionTestUtils.setField(this, "id", paymentId)
            ReflectionTestUtils.setField(this, "status", PaymentStatus.REFUNDED)
            ReflectionTestUtils.setField(this, "refundedAt", refundedAt)
            ReflectionTestUtils.setField(this, "description", "환불 완료")
        }

        given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment))

        //when
        val result = paymentService.getPayment(paymentId)

        //then
        then(paymentRepository).should().findById(paymentId)

        assertThat(result.paymentId).isEqualTo(paymentId)
        assertThat(result.status).isEqualTo(PaymentStatus.REFUNDED)
        assertThat(result.amount).isEqualTo(10000L)
        assertThat(result.method).isEqualTo("카드")
        assertThat(result.occurredAt).isEqualTo(refundedAt)
        assertThat(result.description).isEqualTo("환불 완료")
    }

    @Test
    fun `결제 조회시 method가 null인 경우 빈 문자열로 조회된다`() {
        //given
        val paymentId = 1L
        val requestedAt = LocalDateTime.of(2025, 8, 3, 9, 0, 0)

        val payment = Payment(
            userId = 2000L,
            orderId = 1000L,
            paymentKey = "payment-key-123",
            amount = 10000L,
            method = null,
            requestedAt = requestedAt
        ).apply {
            ReflectionTestUtils.setField(this, "id", paymentId)
            ReflectionTestUtils.setField(this, "status", PaymentStatus.PENDING)
        }

        given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment))

        //when
        val result = paymentService.getPayment(paymentId)

        //then
        then(paymentRepository).should().findById(paymentId)

        assertThat(result.paymentId).isEqualTo(paymentId)
        assertThat(result.method).isEqualTo("")
        assertThat(result.description).isEqualTo("")
    }

    @Test
    fun `결제 로그 조회에 성공한다`() {
        //given
        val paymentId = 1L
        val createdAt1 = LocalDateTime.of(2025, 8, 3, 9, 0, 0)
        val createdAt2 = LocalDateTime.of(2025, 8, 3, 9, 5, 0)
        val createdAt3 = LocalDateTime.of(2025, 8, 3, 9, 10, 0)

        val paymentLog1 = PaymentLog.request(
            paymentId = paymentId,
            message = "결제 요청: orderId = 1000"
        ).apply {
            ReflectionTestUtils.setField(this, "id", 1L)
            ReflectionTestUtils.setField(this, "createdAt", createdAt1)
        }

        val paymentLog2 = PaymentLog.response(
            paymentId = paymentId,
            message = "결제 승인 완료: orderId = 1000, tossPaymentKey = payment-key-123"
        ).apply {
            ReflectionTestUtils.setField(this, "id", 2L)
            ReflectionTestUtils.setField(this, "createdAt", createdAt2)
        }

        val paymentLog3 = PaymentLog.error(
            paymentId = paymentId,
            message = "결제 실패: 잔액 부족"
        ).apply {
            ReflectionTestUtils.setField(this, "id", 3L)
            ReflectionTestUtils.setField(this, "createdAt", createdAt3)
        }

        val logs = listOf(paymentLog3, paymentLog2, paymentLog1) // ID 역순으로 정렬된 상태

        given(paymentLogRepository.findAllByPaymentIdOrderByIdDesc(paymentId, PageRequest.of(0, 20))).willReturn(logs)

        //when
        val result = paymentService.getPaymentLogs(paymentId)

        //then
        then(paymentLogRepository).should().findAllByPaymentIdOrderByIdDesc(paymentId, PageRequest.of(0, 20))

        assertThat(result).hasSize(3)
        assertThat(result[0].logType).isEqualTo(LogType.ERROR)
        assertThat(result[0].message).isEqualTo("결제 실패: 잔액 부족")
        assertThat(result[0].createdAt).isEqualTo(createdAt3)
        assertThat(result[1].logType).isEqualTo(LogType.RESPONSE)
        assertThat(result[1].message).isEqualTo("결제 승인 완료: orderId = 1000, tossPaymentKey = payment-key-123")
        assertThat(result[1].createdAt).isEqualTo(createdAt2)
        assertThat(result[2].logType).isEqualTo(LogType.REQUEST)
        assertThat(result[2].message).isEqualTo("결제 요청: orderId = 1000")
        assertThat(result[2].createdAt).isEqualTo(createdAt1)
    }

    @Test
    fun `결제 로그 조회에 성공한다 로그가 없는 경우 빈 리스트를 반환한다`() {
        //given
        val paymentId = 1L
        given(paymentLogRepository.findAllByPaymentIdOrderByIdDesc(paymentId, PageRequest.of(0, 20))).willReturn(emptyList())

        //when
        val result = paymentService.getPaymentLogs(paymentId)

        //then
        then(paymentLogRepository).should().findAllByPaymentIdOrderByIdDesc(paymentId, PageRequest.of(0, 20))
        assertThat(result).isEmpty()
    }

    @Test
    fun `결제 실패 처리에 성공한다`() {
        //given
        val paymentId = 1L
        val failureReason = "결제 승인 실패: 카드 한도 초과"
        val now = LocalDateTime.of(2025, 8, 3, 9, 0, 0)
        val requestedAt = LocalDateTime.of(2025, 8, 3, 8, 55, 0)

        val payment = Payment(
            userId = 2000L,
            orderId = 1000L,
            paymentKey = "payment-key-123",
            amount = 10000L,
            method = "카드",
            requestedAt = requestedAt
        ).apply {
            ReflectionTestUtils.setField(this, "id", paymentId)
        }

        given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment))
        given(timeProvider.now()).willReturn(now)

        //when
        val result = paymentService.failPayment(paymentId, failureReason)

        //then
        then(paymentRepository).should().findById(paymentId)
        then(paymentRepository).should().save(payment)
        then(paymentLogRepository).should().save(any<PaymentLog>())

        assertThat(result).isTrue()
        assertThat(payment.status).isEqualTo(PaymentStatus.FAIL)
        assertThat(payment.failedAt).isEqualTo(now)
        assertThat(payment.failReason).isEqualTo(failureReason)
    }

    @Test
    fun `결제 실패 처리시 결제가 존재하지 않으면 예외가 발생한다`() {
        //given
        val paymentId = 1L
        val failureReason = "결제 승인 실패"

        given(paymentRepository.findById(paymentId)).willReturn(Optional.empty())

        //when & then
        val result = assertThrows<ApplicationException> {
            paymentService.failPayment(paymentId, failureReason)
        }

        then(paymentRepository).should().findById(paymentId)

        assertThat(result.errorCode).isEqualTo(ErrorCode.PAYMENT_NOT_FOUND)
        assertThat(result.message).isEqualTo("Payments not found [id = $paymentId]")
    }

    @Test
    fun `결제 취소 여부 확인에 성공한다 - 취소된 결제`() {
        //given
        val paymentId = 1L
        val payment = Payment(
            userId = 2000L,
            orderId = 1000L,
            paymentKey = "payment-key-123",
            amount = 10000L,
            method = "카드",
            requestedAt = LocalDateTime.now()
        ).apply {
            ReflectionTestUtils.setField(this, "id", paymentId)
            ReflectionTestUtils.setField(this, "status", PaymentStatus.CANCELED)
        }

        given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment))

        //when
        val result = paymentService.isPaymentCanceled(paymentId)

        //then
        then(paymentRepository).should().findById(paymentId)
        assertThat(result).isTrue()
    }

    @Test
    fun `결제 취소 여부 확인에 성공한다 - 취소되지 않은 결제`() {
        //given
        val paymentId = 1L
        val payment = Payment(
            userId = 2000L,
            orderId = 1000L,
            paymentKey = "payment-key-123",
            amount = 10000L,
            method = "카드",
            requestedAt = LocalDateTime.now()
        ).apply {
            ReflectionTestUtils.setField(this, "id", paymentId)
            ReflectionTestUtils.setField(this, "status", PaymentStatus.SUCCESS)
        }

        given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment))

        //when
        val result = paymentService.isPaymentCanceled(paymentId)

        //then
        then(paymentRepository).should().findById(paymentId)
        assertThat(result).isFalse()
    }

    @Test
    fun `결제 취소 여부 확인시 결제가 존재하지 않으면 예외가 발생한다`() {
        //given
        val paymentId = 1L
        given(paymentRepository.findById(paymentId)).willReturn(Optional.empty())

        //when & then
        val result = assertThrows<ApplicationException> {
            paymentService.isPaymentCanceled(paymentId)
        }

        then(paymentRepository).should().findById(paymentId)

        assertThat(result.errorCode).isEqualTo(ErrorCode.PAYMENT_NOT_FOUND)
        assertThat(result.message).isEqualTo("Payment not found")
    }

}
