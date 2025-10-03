package hana.lovepet.paymentservice.infrastructure.webclient.payment.impl

import hana.lovepet.paymentservice.infrastructure.webclient.payment.TossClient
import hana.lovepet.paymentservice.infrastructure.webclient.payment.dto.request.TossPaymentConfirmRequest
import hana.lovepet.paymentservice.infrastructure.webclient.payment.dto.response.TossPaymentCancelResponse
import hana.lovepet.paymentservice.infrastructure.webclient.payment.dto.response.TossPaymentConfirmResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * K6 부하테스트용 Mock TossClient
 * 실제 PG 요청 없이 즉시 성공 응답을 반환합니다.
 */
@Component
@Profile("load-test")
@Primary
class MockTossClient : TossClient {

    private val log: Logger = LoggerFactory.getLogger(MockTossClient::class.java)

    override fun confirm(tossPaymentConfirmRequest: TossPaymentConfirmRequest): TossPaymentConfirmResponse {
        log.info("[MOCK] 토스페이먼츠 결제 승인 요청: orderId=${tossPaymentConfirmRequest.orderId}, amount=${tossPaymentConfirmRequest.amount}")

        // 실제 PG 호출 없이 즉시 성공 응답 반환
        return TossPaymentConfirmResponse(
            paymentKey = tossPaymentConfirmRequest.paymentKey,
                    orderId = tossPaymentConfirmRequest.orderId,
                    status = "DONE",
                    totalAmount = tossPaymentConfirmRequest.amount,
                    method = "카카오페이",
                    requestedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    approvedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
        )
    }

    override fun cancel(paymentKey: String, cancelReason: String): TossPaymentCancelResponse {
        log.info("[MOCK] 토스페이먼츠 결제 취소 요청: paymentKey=$paymentKey, reason=$cancelReason")

        // 실제 PG 호출 없이 즉시 성공 응답 반환
        return TossPaymentCancelResponse(
            paymentKey = paymentKey,
            orderId = "mock-order-${UUID.randomUUID()}",
            status = "CANCELED",
            totalAmount = 10000L,
            cancels = listOf(
                TossPaymentCancelResponse.CancelDetail(
                    cancelAmount = 10000L,
                    cancelReason = cancelReason,
                    canceledAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    transactionKey = "mock-txn-${UUID.randomUUID()}",
                    cancelStatus = "DONE"
                )
            ),
            balanceAmount = 1000L,
            canceledAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
        )
    }
}