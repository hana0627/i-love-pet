package hana.lovepet.paymentservice.api.payment.service.impl

import hana.lovepet.orderservice.common.exception.constant.ErrorCode
import hana.lovepet.paymentservice.api.payment.controller.dto.request.PaymentCancelRequest
import hana.lovepet.paymentservice.api.payment.controller.dto.request.PaymentRefundRequest
import hana.lovepet.paymentservice.api.payment.controller.dto.response.ConfirmPaymentResponse
import hana.lovepet.paymentservice.api.payment.controller.dto.response.GetPaymentLogResponse
import hana.lovepet.paymentservice.api.payment.controller.dto.response.PaymentCancelResponse
import hana.lovepet.paymentservice.api.payment.controller.dto.response.PreparePaymentResponse
import hana.lovepet.paymentservice.api.payment.controller.dto.response.PaymentRefundResponse
import hana.lovepet.paymentservice.api.payment.controller.dto.response.GetPaymentResponse
import hana.lovepet.paymentservice.api.payment.domain.Payment
import hana.lovepet.paymentservice.api.payment.domain.PaymentLog
import hana.lovepet.paymentservice.api.payment.domain.constant.PaymentStatus
import hana.lovepet.paymentservice.api.payment.domain.constant.PaymentStatus.*
import hana.lovepet.paymentservice.api.payment.repository.PaymentLogRepository
import hana.lovepet.paymentservice.api.payment.repository.PaymentRepository
import hana.lovepet.paymentservice.api.payment.service.PaymentService
import hana.lovepet.paymentservice.common.clock.TimeProvider
import hana.lovepet.paymentservice.common.exception.ApplicationException
//import hana.lovepet.paymentservice.common.exception.PgCommunicationException
import hana.lovepet.paymentservice.common.uuid.UUIDGenerator
import hana.lovepet.paymentservice.infrastructure.kafka.`in`.dto.PaymentCancelEvent
import hana.lovepet.paymentservice.infrastructure.kafka.out.dto.PaymentCanceledEvent
import hana.lovepet.paymentservice.infrastructure.kafka.out.dto.PaymentConfirmedEvent
import hana.lovepet.paymentservice.infrastructure.kafka.out.dto.PaymentPreparedEvent
import hana.lovepet.paymentservice.infrastructure.webclient.payment.TossClient
import hana.lovepet.paymentservice.infrastructure.webclient.payment.dto.request.TossPaymentConfirmRequest
//import hana.lovepet.paymentservice.infrastructure.webclient.payment.dto.response.PgCancelResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
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
    private val tossClient: TossClient,

//    private val paymentEventPublisher: PaymentEventPublisher,
    private val applicationEventPublisher: ApplicationEventPublisher,
) : PaymentService {

    private val log: Logger = LoggerFactory.getLogger(PaymentServiceImpl::class.java)

    @Transactional
    override fun preparePayment(
        userId: Long,
        orderId: Long,
        amount: Long,
        method: String,
    ): PreparePaymentResponse {
        // 0. 멱등처리
        val foundPayment = paymentRepository.findByOrderId(orderId)
        if (foundPayment != null) {
            log.warn("이미 결제가 준비된 주문: orderId=$orderId")
            return PreparePaymentResponse(
                paymentId = foundPayment.id!!,
            )
        }

        // TODO 테스트용 실패
        if (amount == 999L) {
            throw ApplicationException(ErrorCode.UNHEALTHY_SERVER_COMMUNICATION, "테스트용 결제 준비 실패")
        }

        // 1. 임시 Payment키 생성
        val tempPaymentKey = uuidGenerator.generate()
        // 2. Payment 초기 엔티티 생성
        val payment = Payment(
            userId = userId,
            orderId = orderId,
            paymentKey = tempPaymentKey,
            amount = amount,
            method = method,
            requestedAt = timeProvider.now()
        )
        paymentRepository.save(payment)

        // 3. PG사 요청로그 저장
        paymentLogRepository.save(
            PaymentLog.request(
                paymentId = payment.id!!,
                message = "결제 요청 : orderId = ${payment.orderId}"
            )
        )


        applicationEventPublisher.publishEvent(
            PaymentPreparedEvent(
                eventId = uuidGenerator.generate(),
                occurredAt = payment.requestedAt,
                orderId = payment.orderId,
                paymentId = payment.id!!,
                idempotencyKey = payment.paymentKey,
            )
        )

        return PreparePaymentResponse(
            paymentId = payment.id!!,
        )
    }


    @Transactional(noRollbackFor = [ApplicationException::class])
    override fun confirmPayment(orderId: Long, paymentId: Long, orderNo: String, paymentKey: String, amount: Long): ConfirmPaymentResponse {
        val payment = paymentRepository.findById(paymentId)
            .orElseThrow{ ApplicationException(ErrorCode.PAYMENT_NOT_FOUND, ErrorCode.PAYMENT_NOT_FOUND.message) }

        // 결제확정 API 호출
        val tossResponse = try {
            tossClient.confirm(
                TossPaymentConfirmRequest(
                    orderId = orderNo,
                    amount = amount,
                    paymentKey = paymentKey
                )
            )
        } catch (e: ApplicationException) {
            // 결제 승인 실패 로그 저장
            val paymentLog = PaymentLog.response(
                paymentId = payment.id!!,
                message = "결제 승인 실패: ${e.message}"
            )

            payment.fail(
                timeProvider = timeProvider,
                paymentKey = paymentKey,
                failReason = e.message
            )
            paymentRepository.save(payment)
            paymentLogRepository.save(paymentLog)
            throw e
        }

        if (tossResponse.status != "DONE") {
            val errorMessage = "결제 승인 상태 오류: ${tossResponse.status}"
            val paymentLog = PaymentLog.response(
                paymentId = payment.id!!,
                message = errorMessage
            )
            payment.fail(
                timeProvider = timeProvider,
                paymentKey = paymentKey,
                failReason = errorMessage
            )
            paymentRepository.save(payment)
            paymentLogRepository.save(paymentLog)
            throw ApplicationException(ErrorCode.UNHEALTHY_PG_COMMUNICATION, errorMessage)
        }

        // 금액 검증
        if (tossResponse.totalAmount != amount) {
            val errorMessage = "결제 금액 불일치: expected=${amount}, actual=${tossResponse.totalAmount}"
            val paymentLog = PaymentLog.response(
                paymentId = payment.id!!,
                message = errorMessage
            )
            payment.fail(
                timeProvider = timeProvider,
                paymentKey = paymentKey,
                failReason = errorMessage
            )
            paymentRepository.save(payment)
            paymentLogRepository.save(paymentLog)
            throw ApplicationException(ErrorCode.UNHEALTHY_PG_COMMUNICATION, errorMessage)
        }


        payment.approve(
            timeProvider = timeProvider,
            paymentKey = paymentKey
        )

        val paymentLog = PaymentLog.response(
            paymentId = payment.id!!,
            message = "결제 승인 완료: orderId = ${payment.orderId}, tossPaymentKey = ${tossResponse.paymentKey}"
        )

        paymentRepository.save(payment)
        paymentLogRepository.save(paymentLog)

        // 이벤트 발행
        applicationEventPublisher.publishEvent(
            PaymentConfirmedEvent(
                eventId = uuidGenerator.generate(),
                occurredAt = payment.approvedAt!!,
                orderId = orderId,
                paymentId = payment.id!!,
                idempotencyKey = payment.paymentKey,
            )
        )
        return ConfirmPaymentResponse(
            paymentId = payment.id!!,
        )
    }

    @Transactional(noRollbackFor = [ApplicationException::class])
    override fun cancelPayment(paymentCancelEvent: PaymentCancelEvent): PaymentCancelResponse {
        val payment = paymentRepository.findById(paymentCancelEvent.paymentId)
            .orElseThrow { ApplicationException(ErrorCode.PAYMENT_NOT_FOUND, "Payments not found [id = ${paymentCancelEvent.paymentId}]") }

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
                message = "cancel request: reason='${paymentCancelEvent.refundReason}'"
            )
        )


        return try {
            val tossResponse = tossClient.cancel(payment.paymentKey, paymentCancelEvent.refundReason)


            if (tossResponse.status != "CANCELED") {
                log.error("토스페이먼츠 결제 취소 오류 : ${tossResponse}")
                val errorMessage = "토스페이먼츠 취소 상태 오류: ${tossResponse.status}"
                paymentLogRepository.save(
                    PaymentLog.error(
                        paymentId = payment.id!!,
                        message = errorMessage
                    )
                )

                throw ApplicationException(ErrorCode.UNHEALTHY_PG_COMMUNICATION, errorMessage)
            }

            // STEP6. 취소 상세 정보 추출 (가장 최근 취소 건)
            val latestCancel = tossResponse.cancels.maxByOrNull { it.canceledAt }
                ?: throw ApplicationException(ErrorCode.UNHEALTHY_PG_COMMUNICATION, "취소 상세 정보를 찾을 수 없습니다.")

            if (latestCancel.cancelStatus != "DONE") {
                log.error("토스페이먼츠 결제 취소 오류 : ${tossResponse}")
                val errorMessage = "취소 처리 상태 오류: ${latestCancel.cancelStatus}"
                paymentLogRepository.save(
                    PaymentLog.response(
                        paymentId = payment.id!!,
                        message = errorMessage
                    )
                )
                throw ApplicationException(ErrorCode.UNHEALTHY_PG_COMMUNICATION, errorMessage)
            }

            payment.cancel(
                timeProvider = timeProvider,
                description = paymentCancelEvent.refundReason
            )

            paymentLogRepository.save(
                PaymentLog.response(
                    paymentId = payment.id!!,
                    message = "결제 취소 완료: reason='${paymentCancelEvent.refundReason}', transactionKey=${latestCancel.transactionKey}, cancelAmount=${latestCancel.cancelAmount}"
                )
            )

            applicationEventPublisher.publishEvent(
                PaymentCanceledEvent(
                    eventId = uuidGenerator.generate(),
                    cancelAt = payment.canceledAt!!,
                    orderId = paymentCancelEvent.orderId,
                    paymentId = payment.id!!,
                    idempotencyKey = payment.paymentKey,
                )
            )

            return PaymentCancelResponse(
                paymentId = payment.id!!,
                canceledAt = timeProvider.now(),
                transactionKey = latestCancel.transactionKey,
                message = "결제 취소 완료"
            )

        } catch (e: ApplicationException) {
            // STEP9. PG 통신 실패 로그 저장
            paymentLogRepository.save(
                PaymentLog.response(
                    paymentId = payment.id!!,
                    message = "토스페이먼츠 취소 실패: ${e.message}"
                )
            )
            log.error("토스페이먼츠 결제 취소 실패: paymentId=${payment.id!!}, reason=${paymentCancelEvent.refundReason}, error=${e.message}")
            throw e
        } catch (e: Exception) {
            // STEP10. 기타 예외 처리
            paymentLogRepository.save(
                PaymentLog.response(
                    paymentId = payment.id!!,
                    message = "결제 취소 중 예외 발생: ${e.message}"
                )
            )
            log.error("결제 취소 중 예외 발생: paymentId=${payment.id!!}", e)
            throw ApplicationException(ErrorCode.UNHEALTHY_PG_COMMUNICATION, "결제 취소 중 오류가 발생했습니다.")
        }
    }

    override fun getPayment(paymentId: Long): GetPaymentResponse {
        val payment = paymentRepository.findById(paymentId)
            .orElseThrow { ApplicationException(ErrorCode.PAYMENT_NOT_FOUND, "Payments not found [id = $paymentId]") }

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

    @Transactional
    override fun failPayment(
        paymentId: Long,
        failureReason: String,
//        failPaymentRequest: FailPaymentRequest,
    ): Boolean {
        val payment = paymentRepository.findById(paymentId)
            .orElseThrow { ApplicationException(ErrorCode.PAYMENT_NOT_FOUND, "Payments not found [id = $paymentId]") }

        payment.fail(
            timeProvider = timeProvider,
            paymentKey = null,
//            failReason = "code = ${failPaymentRequest.code}, message = ${failPaymentRequest.message}"
            failReason = failureReason
        )

        val paymentLog = PaymentLog.error(
            paymentId = paymentId,
//            message = "code = ${failPaymentRequest.code}, message = ${failPaymentRequest.message}"
            message = failureReason
        )

        paymentRepository.save(payment)
        paymentLogRepository.save(paymentLog)

        return true
    }


    @Transactional(readOnly = true)
    override fun isPaymentCanceled(paymentId: Long): Boolean {
        val payment = paymentRepository.findById(paymentId)
            .orElseThrow { ApplicationException(ErrorCode.PAYMENT_NOT_FOUND, "Payment not found") }
        return payment.status == PaymentStatus.CANCELED
    }


    override fun refundPayment(paymentId: Long, paymentRefundRequest: PaymentRefundRequest): PaymentRefundResponse {
        TODO("Not yet implemented")
    }

//    @Transactional(noRollbackFor = [ApplicationException::class])
//    override fun confirmPayment(paymentId: Long, confirmPaymentRequest: ConfirmPaymentRequest): ConfirmPaymentResponse {
//        val payment = paymentRepository.findById(paymentId)
//            .orElseThrow { ApplicationException(ErrorCode.PAYMENT_NOT_FOUND, "Payments not found [id = $paymentId]") }
//
//        // 결제확정 API 호출
//        val tossResponse = try {
//            tossClient.confirm(
//                TossPaymentConfirmRequest(
//                    orderId = confirmPaymentRequest.orderNo,
//                    amount = confirmPaymentRequest.amount,
//                    paymentKey = confirmPaymentRequest.paymentKey
//                )
//            )
//        } catch (e: ApplicationException) {
//            // 결제 승인 실패 로그 저장
//            val paymentLog = PaymentLog.response(
//                paymentId = paymentId,
//                message = "결제 승인 실패: ${e.message}"
//            )
//            payment.fail(
//                timeProvider = timeProvider,
//                paymentKey = confirmPaymentRequest.paymentKey,
//                failReason = e.message
//            )
//            paymentRepository.save(payment)
//            paymentLogRepository.save(paymentLog)
//            throw e
//        }
//
//        if (tossResponse.status != "DONE") {
//            val errorMessage = "결제 승인 상태 오류: ${tossResponse.status}"
//            val paymentLog = PaymentLog.response(
//                paymentId = paymentId,
//                message = errorMessage
//            )
//            payment.fail(
//                timeProvider = timeProvider,
//                paymentKey = confirmPaymentRequest.paymentKey,
//                failReason = errorMessage
//            )
//            paymentRepository.save(payment)
//            paymentLogRepository.save(paymentLog)
//            throw ApplicationException(ErrorCode.UNHEALTHY_PG_COMMUNICATION, errorMessage)
//        }
//
//        // 금액 검증
//        if (tossResponse.totalAmount != confirmPaymentRequest.amount) {
//            val errorMessage = "결제 금액 불일치: expected=${confirmPaymentRequest.amount}, actual=${tossResponse.totalAmount}"
//            val paymentLog = PaymentLog.response(
//                paymentId = paymentId,
//                message = errorMessage
//            )
//            payment.fail(
//                timeProvider = timeProvider,
//                paymentKey = confirmPaymentRequest.paymentKey,
//                failReason = errorMessage
//            )
//            paymentRepository.save(payment)
//            paymentLogRepository.save(paymentLog)
//            throw ApplicationException(ErrorCode.UNHEALTHY_PG_COMMUNICATION, errorMessage)
//        }
//
//
//        payment.approve(
//            timeProvider = timeProvider,
//            paymentKey = confirmPaymentRequest.paymentKey
//        )
//
//        val paymentLog = PaymentLog.response(
//            paymentId = paymentId,
//            message = "결제 승인 완료: orderId = ${payment.orderId}, tossPaymentKey = ${tossResponse.paymentKey}"
//        )
//
//        paymentRepository.save(payment)
//        paymentLogRepository.save(paymentLog)
//
//        return ConfirmPaymentResponse(
//            paymentId = paymentId,
//        )
//    }
}
