package hana.lovepet.paymentservice.api.payment.service

import hana.lovepet.paymentservice.api.payment.controller.dto.request.PaymentCancelRequest
import hana.lovepet.paymentservice.api.payment.controller.dto.request.PaymentCreateRequest
import hana.lovepet.paymentservice.api.payment.domain.Payment
import hana.lovepet.paymentservice.api.payment.domain.PaymentLog
import hana.lovepet.paymentservice.api.payment.domain.constant.PaymentStatus
import hana.lovepet.paymentservice.api.payment.repository.PaymentLogRepository
import hana.lovepet.paymentservice.api.payment.repository.PaymentRepository
import hana.lovepet.paymentservice.api.payment.service.impl.PaymentServiceImpl
import hana.lovepet.paymentservice.common.clock.TimeProvider
import hana.lovepet.paymentservice.common.exception.PgCommunicationException
import hana.lovepet.paymentservice.common.uuid.UUIDGenerator
import hana.lovepet.paymentservice.infrastructure.webclient.payment.PgClient
import hana.lovepet.paymentservice.infrastructure.webclient.payment.dto.request.PgApproveRequest
import hana.lovepet.paymentservice.infrastructure.webclient.payment.dto.response.PgApproveResponse
import hana.lovepet.paymentservice.infrastructure.webclient.payment.dto.response.PgCancelResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.then
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.junit.jupiter.MockitoExtension
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
    lateinit var pgClient: PgClient
    lateinit var paymentService: PaymentService

    @BeforeEach
    fun setUp() {
        paymentService =
            PaymentServiceImpl(paymentRepository, paymentLogRepository, timeProvider, uuidGenerator, pgClient)
    }

    @Test
    fun `PG사 결제에 성공한다`() {
        //given
        given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 8, 3, 9, 0, 0))
        given(uuidGenerator.generate()).willReturn("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")

        val request = PaymentCreateRequest.fixture()

        val payment = Payment(
            userId = request.userId,
            orderId = request.orderId,
            paymentKey = uuidGenerator.generate(),
            amount = request.amount,
            method = request.method,
            requestedAt = timeProvider.now(),
        ).apply { id = 1L }

        val pgRequest = PgApproveRequest(
            orderId = payment.orderId,
            userId = payment.userId,
            amount = payment.amount,
            method = payment.method
        )

        val pgSuccessResponse = PgApproveResponse.Success(
            paymentKey = "pg-success-key",
            amount = request.amount,
            method = request.method!!,
//            rawJson = "{\"result\":\"ok\"}"
        )

        given(paymentRepository.save(any())).willAnswer { invocation ->
            val saved = invocation.arguments[0] as Payment
            ReflectionTestUtils.setField(saved, "id", 1L)
            saved
        }

        given(paymentLogRepository.save(any())).willAnswer { invocation ->
            val savedLog = invocation.arguments[0] as PaymentLog
            ReflectionTestUtils.setField(savedLog, "id", 2L)
            savedLog
        }

        given(pgClient.approve(pgRequest)).willReturn(pgSuccessResponse)

        //when
        val result = paymentService.createPayment(request)

        //then
        then(paymentRepository).should(times(2)).save(any())
        then(paymentLogRepository).should(times(2)).save(any())
        then(pgClient).should().approve(pgRequest)

        assertThat(result.paymentKey).isEqualTo("pg-success-key")
        assertThat(result.paymentId).isEqualTo(payment.id)
        assertThat(result.isSuccess).isEqualTo(true)
        assertThat(result.failReason).isNull()
    }

    @Test
    fun `잔액부족 등이 이유로 결제 실패시 Fail응답을 반환한다`() {
        //given
        given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 8, 3, 9, 0, 0))
        given(uuidGenerator.generate()).willReturn("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")

        val request = PaymentCreateRequest.fixture()

        val payment = Payment(
            userId = request.userId,
            orderId = request.orderId,
            paymentKey = uuidGenerator.generate(),
            amount = request.amount,
            method = request.method,
            requestedAt = timeProvider.now()
        ).apply { id = 1L }

        val pgRequest = PgApproveRequest(
            orderId = payment.orderId,
            userId = payment.userId,
            amount = payment.amount,
            method = payment.method
        )

        val pgFailResponse = PgApproveResponse.Fail(
            paymentKey = "pg-fail-key",
            code = "ok",
            message = "잔액 부족"
//            rawJson = "{\"result\":\"ok\"}",
//            failReason = "잔액 부족"
        )

        given(paymentRepository.save(any())).willAnswer { invocation ->
            val saved = invocation.arguments[0] as Payment
            ReflectionTestUtils.setField(saved, "id", 1L)
            saved
        }

        given(paymentLogRepository.save(any())).willAnswer { invocation ->
            val savedLog = invocation.arguments[0] as PaymentLog
            ReflectionTestUtils.setField(savedLog, "id", 2L)
            savedLog
        }

        given(pgClient.approve(pgRequest)).willReturn(pgFailResponse)

        //when
        val result = paymentService.createPayment(request)

        //then
        then(paymentRepository).should(times(2)).save(any())
        then(paymentLogRepository).should(times(2)).save(any())
        then(pgClient).should().approve(pgRequest)

        assertThat(result.paymentKey).isEqualTo("pg-fail-key")
        assertThat(result.paymentId).isEqualTo(payment.id)
        assertThat(result.isSuccess).isEqualTo(false)
        assertThat(result.failReason).isEqualTo("잔액 부족")
    }

    @Test
    fun `PG사 통신 예외가 발생하면 예외를 던지고 결제를 실패 상태로 저장한다`() {
        //given
        given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 8, 3, 9, 0, 0))
        given(uuidGenerator.generate()).willReturn("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")

        val request = PaymentCreateRequest.fixture()

        given(paymentRepository.save(any())).willAnswer {
            val saved = it.arguments[0] as Payment
            ReflectionTestUtils.setField(saved, "id", 1L)
            saved
        }

        given(paymentLogRepository.save(any())).willAnswer {
            val savedLog = it.arguments[0] as PaymentLog
            ReflectionTestUtils.setField(savedLog, "id", 2L)
            savedLog
        }

        val pgRequest = PgApproveRequest(
            orderId = request.orderId,
            userId = request.userId,
            amount = request.amount,
            method = request.method
        )

        given(pgClient.approve(pgRequest)).willThrow(PgCommunicationException("PG 통신 실패"))

        //when
        val result = assertThrows<PgCommunicationException> {paymentService.createPayment(request)}

        //then
        then(paymentRepository).should(times(2)).save(any())
        then(paymentLogRepository).should(times(2)).save(any())
        then(pgClient).should().approve(pgRequest)

        assertThat(result.message).isEqualTo("PG 통신 실패")

    }
    @Test
    fun `PG사 통신 예외가 발생하면 예외를 던지고 결제를 실패 상태로 저장한다2`() {
        //given
        given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 8, 3, 9, 0, 0))
        given(uuidGenerator.generate()).willReturn("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")

        val request = PaymentCreateRequest.fixture()

        given(paymentRepository.save(any())).willAnswer {
            val saved = it.arguments[0] as Payment
            ReflectionTestUtils.setField(saved, "id", 1L)
            saved
        }

        given(paymentLogRepository.save(any())).willAnswer {
            val savedLog = it.arguments[0] as PaymentLog
            ReflectionTestUtils.setField(savedLog, "id", 2L)
            savedLog
        }

        val pgRequest = PgApproveRequest(
            orderId = request.orderId,
            userId = request.userId,
            amount = request.amount,
            method = request.method
        )

        given(pgClient.approve(pgRequest)).willThrow(PgCommunicationException(null,null))

        //when
        val result = assertThrows<PgCommunicationException> {paymentService.createPayment(request)}

        //then
        then(paymentRepository).should(times(2)).save(any())
        then(paymentLogRepository).should(times(2)).save(any())
        then(pgClient).should().approve(pgRequest)

        assertThat(result).isInstanceOf(PgCommunicationException::class.java)
    }


    @Test
    fun `결제취소에 성공한다`() {
        //given
        given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 8, 3, 9, 0, 0))
        given(uuidGenerator.generate()).willReturn("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")

        val paymentId = 1L
        val paymentCancelRequest = PaymentCancelRequest("재고차감 실패")
        val payment = Payment(
            userId = 2000L,
            orderId = 3000L,
            paymentKey = uuidGenerator.generate(),
            amount = 40000L,
            method = "카드",
            requestedAt = timeProvider.now(),
            approvedAt = timeProvider.now(),
            status = PaymentStatus.SUCCESS,
        ).apply { id = paymentId }

        given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment))

        val paCancelResponse = PgCancelResponse.success(
            paymentKey = payment.paymentKey,
            transactionKey = "transactionKey",
            cancelAt = timeProvider.now()
        )
        given(pgClient.cancel(payment.paymentKey, paymentCancelRequest.refundReason))
            .willReturn(paCancelResponse)

        //when
        val result = paymentService.cancelPayment(paymentId, paymentCancelRequest)

        //then
        assertThat(result.paymentId).isEqualTo(paymentId)
        assertThat(result.transactionKey).isEqualTo(paCancelResponse.transactionKey)
        assertThat(result.canceledAt).isEqualTo(timeProvider.now())
        assertThat(result.message).isEqualTo("성공적으로 취소 되었습니다.")
    }


    @Test
    fun `이미 결제취소가 이루어 졌다면 결제취소가 일어나지 않는다`() {
        //given
        given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 8, 3, 9, 0, 0))
        given(uuidGenerator.generate()).willReturn("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")

        val paymentId = 1L
        val paymentCancelRequest = PaymentCancelRequest("재고차감 실패")
        val payment = Payment(
            userId = 2000L,
            orderId = 3000L,
            paymentKey = uuidGenerator.generate(),
            amount = 40000L,
            method = "카드",
            requestedAt = timeProvider.now(),
            approvedAt = timeProvider.now(),
            canceledAt = timeProvider.now(),
            status = PaymentStatus.CANCELED,
        ).apply { id = paymentId }

        given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment))

        //when
        val result = paymentService.cancelPayment(paymentId, paymentCancelRequest)

        //then
        assertThat(result.paymentId).isEqualTo(paymentId)
        assertThat(result.transactionKey).isNull()
        assertThat(result.canceledAt).isEqualTo(timeProvider.now())
        assertThat(result.message).isEqualTo("이미 취소된 결제입니다.")
    }



    @Test
    fun `이미 결제취소가 되었는데 PG사에 취소요청을 보내도 결제취소가 다시 일어나지 않는다`() {
        //given
        given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 8, 3, 9, 0, 0))
        given(uuidGenerator.generate()).willReturn("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")

        val paymentId = 1L
        val paymentCancelRequest = PaymentCancelRequest("재고차감 실패")
        val payment = Payment(
            userId = 2000L,
            orderId = 3000L,
            paymentKey = uuidGenerator.generate(),
            amount = 40000L,
            method = "카드",
            requestedAt = timeProvider.now(),
            approvedAt = timeProvider.now(),
            status = PaymentStatus.SUCCESS,
        ).apply { id = paymentId }

        given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment))

        val paCancelResponse = PgCancelResponse.fail(
            paymentKey = payment.paymentKey,
            code = "ALREADY_CANCELED_PAYMENT",
            message = "이미 취소된 결제 입니다."
        )
        given(pgClient.cancel(payment.paymentKey, paymentCancelRequest.refundReason))
            .willReturn(paCancelResponse)

        //when
        val result = paymentService.cancelPayment(paymentId, paymentCancelRequest)

        //then
        assertThat(result.paymentId).isEqualTo(paymentId)
        assertThat(result.transactionKey).isNull()
        assertThat(result.canceledAt).isNull()
        assertThat(result.message).isEqualTo(paCancelResponse.message)
    }

    @Test
    fun `PG사 통신 예외가 발생하면 예외를 던지고 결제취소가 진행되지 않는다`() {
        //given
        given(timeProvider.now()).willReturn(LocalDateTime.of(2025, 8, 3, 9, 0, 0))
        given(uuidGenerator.generate()).willReturn("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")

        val paymentId = 1L
        val paymentCancelRequest = PaymentCancelRequest("재고차감 실패")
        val payment = Payment(
            userId = 2000L,
            orderId = 3000L,
            paymentKey = uuidGenerator.generate(),
            amount = 40000L,
            method = "카드",
            requestedAt = timeProvider.now(),
            approvedAt = timeProvider.now(),
            status = PaymentStatus.SUCCESS,
        ).apply { id = paymentId }

        given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment))

        val paCancelResponse = PgCancelResponse.fail(
            paymentKey = payment.paymentKey,
            code = "ALREADY_CANCELED_PAYMENT",
            message = "이미 취소된 결제 입니다."
        )
        given(pgClient.cancel(payment.paymentKey, paymentCancelRequest.refundReason)).willThrow(PgCommunicationException("PG사의 응답이 없습니다."))

        //when
        val result = assertThrows<PgCommunicationException> {paymentService.cancelPayment(paymentId, paymentCancelRequest)}

        //then
        assertThat(result.message).isEqualTo("PG사의 응답이 없습니다.")
    }

}
