package hana.lovepet.paymentservice.api.payment.service.impl

import hana.lovepet.paymentservice.api.payment.controller.dto.request.PaymentCancelRequest
import hana.lovepet.paymentservice.api.payment.controller.dto.request.PaymentCreateRequest
import hana.lovepet.paymentservice.api.payment.controller.dto.request.PaymentRefundRequest
import hana.lovepet.paymentservice.api.payment.controller.dto.response.PaymentCancelResponse
import hana.lovepet.paymentservice.api.payment.controller.dto.response.PaymentCreateResponse
import hana.lovepet.paymentservice.api.payment.controller.dto.response.PaymentRefundResponse
import hana.lovepet.paymentservice.api.payment.controller.dto.response.PaymentResponse
import hana.lovepet.paymentservice.api.payment.domain.Payment
import hana.lovepet.paymentservice.api.payment.domain.PaymentLog
import hana.lovepet.paymentservice.api.payment.repository.PaymentLogRepository
import hana.lovepet.paymentservice.api.payment.repository.PaymentRepository
import hana.lovepet.paymentservice.api.payment.service.PaymentService
import hana.lovepet.paymentservice.common.clock.TimeProvider
import hana.lovepet.paymentservice.common.exception.PgCommunicationException
import hana.lovepet.paymentservice.common.uuid.UUIDGenerator
import hana.lovepet.paymentservice.infrastructure.webclient.payment.PgClient
import hana.lovepet.paymentservice.infrastructure.webclient.payment.dto.request.PgApproveRequest
import hana.lovepet.paymentservice.infrastructure.webclient.payment.dto.response.PgApproveResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
@Transactional
class PaymentServiceImpl(
    private val paymentRepository: PaymentRepository,
    private val paymentLogRepository: PaymentLogRepository,
    private val timeProvider: TimeProvider,
    private val uuidGenerator : UUIDGenerator,
    private val pgClient: PgClient
) : PaymentService {

    override fun createPayment(paymentCreateRequest: PaymentCreateRequest): PaymentCreateResponse {
        // 1. 임시 Payment키 생성
        val tempPaymentKey = uuidGenerator.generate()

        // 2. Payment 초기 엔티티 생성
        val payment = Payment(
            userId = paymentCreateRequest.userId,
            orderId = paymentCreateRequest.orderId,
            paymentKey = tempPaymentKey,
            amount = paymentCreateRequest.amount,
            method = paymentCreateRequest.method,
            requestedAt = timeProvider.now()
        )
        paymentRepository.save(payment)

        // 3. PG사 요청로그 저장
        val pgRequest = PgApproveRequest(
            orderId = payment.orderId,
            userId = payment.userId,
            amount = payment.amount,
            method = payment.method
        )

        paymentLogRepository.save(PaymentLog.request(payment.id!!, pgRequest.toString()))


        // 4. PG 요청
        val pgResponse: PgApproveResponse
        var isSuccess = false

        try {
            pgResponse = pgClient.approve(pgRequest)
        } catch (e: PgCommunicationException) {
            // PG사 통신실패시

            payment.fail(
                timeProvider = timeProvider,
                paymentKey = null,
                failReason = "PG 통신 실패",
                pgResponse = e.message ?: "PG 통신 예외"
            )

            paymentLogRepository.save(PaymentLog.error(payment.id!!, e.message ?: "PG 통신 예외"))
            paymentRepository.save(payment)

            throw e
        }

        // 5. 상태 전이
        if (pgResponse is PgApproveResponse.Success) {
            isSuccess = true
            payment.approve(timeProvider, pgResponse.paymentKey, pgResponse.rawJson)
        }
        if (pgResponse is PgApproveResponse.Fail) {
            isSuccess = false
            payment.fail(timeProvider, pgResponse.paymentKey, pgResponse.failReason, pgResponse.rawJson)
        }

        // 6. PG 응답 로그 저장
        paymentLogRepository.save(PaymentLog.response(payment.id!!, pgResponse.rawJson))

        // 7. 최종 저장 및 응답
        paymentRepository.save(payment)

        return PaymentCreateResponse(
            paymentId = payment.id!!,
            paymentKey = payment.paymentKey,
            isSuccess = isSuccess,
            failReason = payment.failReason
        )
    }

    @Transactional(readOnly = true)
    override fun getPayment(paymentId: Long): PaymentResponse {
        TODO("Not yet implemented")
    }

    override fun cancelPayment(paymentId: Long, paymentCancelRequest: PaymentCancelRequest): PaymentCancelResponse {
        TODO("Not yet implemented")
    }

    override fun refundPayment(paymentId: Long, paymentRefundRequest: PaymentRefundRequest): PaymentRefundResponse {
        TODO("Not yet implemented")
    }
}
