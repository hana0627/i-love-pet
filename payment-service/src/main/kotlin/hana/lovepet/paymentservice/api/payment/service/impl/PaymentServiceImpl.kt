package hana.lovepet.paymentservice.api.payment.service.impl

import hana.lovepet.paymentservice.api.payment.controller.dto.request.PaymentCancelRequest
import hana.lovepet.paymentservice.api.payment.controller.dto.request.PaymentCreateRequest
import hana.lovepet.paymentservice.api.payment.controller.dto.request.PaymentRefundRequest
import hana.lovepet.paymentservice.api.payment.controller.dto.response.GetPaymentLogResponse
import hana.lovepet.paymentservice.api.payment.controller.dto.response.PaymentCancelResponse
import hana.lovepet.paymentservice.api.payment.controller.dto.response.PaymentCreateResponse
import hana.lovepet.paymentservice.api.payment.controller.dto.response.PaymentRefundResponse
import hana.lovepet.paymentservice.api.payment.controller.dto.response.GetPaymentResponse
import hana.lovepet.paymentservice.api.payment.domain.Payment
import hana.lovepet.paymentservice.api.payment.domain.PaymentLog
import hana.lovepet.paymentservice.api.payment.domain.constant.PaymentStatus.*
import hana.lovepet.paymentservice.api.payment.repository.PaymentLogRepository
import hana.lovepet.paymentservice.api.payment.repository.PaymentRepository
import hana.lovepet.paymentservice.api.payment.service.PaymentService
import hana.lovepet.paymentservice.common.clock.TimeProvider
import hana.lovepet.paymentservice.common.exception.PgCommunicationException
import hana.lovepet.paymentservice.common.uuid.UUIDGenerator
import hana.lovepet.paymentservice.infrastructure.webclient.payment.PgClient
import hana.lovepet.paymentservice.infrastructure.webclient.payment.dto.request.PgApproveRequest
import hana.lovepet.paymentservice.infrastructure.webclient.payment.dto.response.PgApproveResponse
import hana.lovepet.paymentservice.infrastructure.webclient.payment.dto.response.PgCancelResponse
import jakarta.persistence.EntityNotFoundException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class PaymentServiceImpl(
    private val paymentRepository: PaymentRepository,
    private val paymentLogRepository: PaymentLogRepository,
    private val timeProvider: TimeProvider,
    private val uuidGenerator: UUIDGenerator,
    private val pgClient: PgClient,
) : PaymentService {

    val log: Logger = LoggerFactory.getLogger(PaymentServiceImpl::class.java)

    @Transactional(noRollbackFor = [PgCommunicationException::class])
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
        paymentLogRepository.save(
            PaymentLog.request(
                paymentId = payment.id!!,
                message = "create information : 'orderId = ${payment.orderId}'"
            )
        )


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
            )

            paymentLogRepository.save(
                PaymentLog.error(
                    paymentId = payment.id!!,
                    message = "create error: reason ='${e.message}'"
                )
            )
            paymentRepository.save(payment)

            throw e
        }

        // 5. 상태 전이
        if (pgResponse is PgApproveResponse.Success) {
            isSuccess = true
            payment.approve(timeProvider, pgResponse.paymentKey)

            // 6. PG 응답 로그 저장
            paymentLogRepository.save(
                PaymentLog.response(
                    paymentId = payment.id!!,
                    message = "create response : Success, 'paymentKey : ${pgResponse.paymentKey}!'",
                )
            )
        }
        if (pgResponse is PgApproveResponse.Fail) {
            isSuccess = false
            payment.fail(timeProvider, pgResponse.paymentKey, pgResponse.message)

            // 6. PG 응답 로그 저장
            paymentLogRepository.save(
                PaymentLog.response(
                    paymentId = payment.id!!,
                    message = "create response : FAIL reason ='${pgResponse.message}', ",
                )
            )
        }


        // 7. 최종 저장 및 응답
        paymentRepository.save(payment)

        return PaymentCreateResponse(
            paymentId = payment.id!!,
            paymentKey = payment.paymentKey,
            isSuccess = isSuccess,
            failReason = payment.failReason
        )
    }

    override fun getPayment(paymentId: Long): GetPaymentResponse {
        val payment = paymentRepository.findById(paymentId)
            .orElseThrow { EntityNotFoundException("Payments not found [id = $paymentId]") }

        return when (payment.status) {
            PENDING -> GetPaymentResponse(
                paymentId = payment.id!!,
                status = payment.status,
                amount = payment.amount,
                method = payment.method ?: "",
                occurredAt = payment.requestedAt,
                description = payment.description ?: "",
            )

            SUCCESS -> GetPaymentResponse(
                paymentId = payment.id!!,
                status = payment.status,
                amount = payment.amount,
                method = payment.method ?: "",
                occurredAt = payment.approvedAt!!,
                description = payment.description ?: "",
            )

            FAIL -> GetPaymentResponse(
                paymentId = payment.id!!,
                status = payment.status,
                amount = payment.amount,
                method = payment.method ?: "",
                occurredAt = payment.failedAt!!,
                description = payment.failReason ?: "",
            )

            CANCELED -> GetPaymentResponse(
                paymentId = payment.id!!,
                status = payment.status,
                amount = payment.amount,
                method = payment.method ?: "",
                occurredAt = payment.canceledAt!!,
                description = payment.description ?: "",
            )

            REFUNDED -> GetPaymentResponse(
                paymentId = payment.id!!,
                status = payment.status,
                amount = payment.amount,
                method = payment.method ?: "",
                occurredAt = payment.refundedAt!!,
                description = payment.description ?: "",
            )
        }
    }

    override fun getPaymentLogs(paymentId: Long): List<GetPaymentLogResponse> {
        val logs = paymentLogRepository
            .findAllByPaymentIdOrderByIdDesc(paymentId, PageRequest.of(0, 20))
        return logs.map {
            GetPaymentLogResponse(
                logType = it.logType,
                message = it.message,
                createdAt = it.createdAt,
            )
        }
    }

    @Transactional(noRollbackFor = [PgCommunicationException::class])
    override fun cancelPayment(paymentId: Long, paymentCancelRequest: PaymentCancelRequest): PaymentCancelResponse {
        val payment = paymentRepository.findById(paymentId)
            .orElseThrow { EntityNotFoundException("Payments not found [id = $paymentId]") }

        // STEP1. 멱등 처리 -- 이미 취소된 건이면 성공요청으로 반환
        if (payment.status == CANCELED) {
            return PaymentCancelResponse(
                paymentId = payment.id!!,
                canceledAt = payment.canceledAt!!,
                transactionKey = null,
                message = "이미 취소된 결제입니다."
            )
        }

        // STEP2. PG 결제취소 요청
        paymentLogRepository.save(
            PaymentLog.request(
                paymentId = payment.id!!,
                message = "cancel request: reason='${paymentCancelRequest.refundReason}'"
            )
        )
        val pgCancelResponse: PgCancelResponse = try {
            pgClient.cancel(payment.paymentKey, paymentCancelRequest.refundReason)
        } catch (e: PgCommunicationException) {
            // PG사 통신실패시
            log.error("PG사 통신실패 수동 처리 필요 ")
            paymentLogRepository.save(
                PaymentLog.error(
                    paymentId = payment.id!!,
                    message = "cancel error: reason ='${e.message}'"
                )
            )
            throw e
        }


        when (pgCancelResponse) {
            is PgCancelResponse.Success -> {
                // STEP3. 결제 취소상태로 변경
                payment.cancel(timeProvider = timeProvider, description = paymentCancelRequest.refundReason)
                // STEP4. 로그 저장
                paymentLogRepository.save(
                    PaymentLog.response(
                        paymentId = payment.id!!,
                        message = "cancel success: transactionKey='${pgCancelResponse.transactionKey}'"
                    )
                )
                paymentRepository.save(payment)

                return PaymentCancelResponse(
                    paymentId = payment.id!!,
                    canceledAt = payment.canceledAt!!,
                    transactionKey = pgCancelResponse.transactionKey,
                    message = "성공적으로 취소 되었습니다."
                )
            }

            is PgCancelResponse.Fail -> {
                paymentLogRepository.save(
                    PaymentLog.response(
                        paymentId = payment.id!!,
                        message = "cancel fail: 'idempotent: already canceled'"
                    )
                )
                return PaymentCancelResponse(
                    paymentId = payment.id!!,
                    message = pgCancelResponse.message
                )
            }
        }
    }


    override fun refundPayment(paymentId: Long, paymentRefundRequest: PaymentRefundRequest): PaymentRefundResponse {
        TODO("Not yet implemented")
    }

}
