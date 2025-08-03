package hana.lovepet.paymentservice.api.payment.controller.dto.response

import hana.lovepet.paymentservice.api.payment.domain.constant.PaymentStatus
import java.time.LocalDateTime

data class PaymentResponse(
    val paymentId: Long,
    val orderId: Long,
    val userId: Long,
    val paymentKey: String,
    val amount: Long,
    val status: PaymentStatus,
    val approvedAt: LocalDateTime?,
    val failedAt: LocalDateTime?,
    val canceledAt: LocalDateTime?,
    val refundedAt: LocalDateTime?,
    val failReason: String?,
    val description: String?,
    val pgResponse: String?
) {
}
