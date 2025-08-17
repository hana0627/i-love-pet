package hana.lovepet.paymentservice.api.payment.controller.dto.response

import hana.lovepet.paymentservice.api.payment.domain.constant.PaymentStatus
import java.time.LocalDateTime

data class GetPaymentResponse(
    val paymentId: Long,
    var status : PaymentStatus,
    val amount: Long,
    val method: String,
    val occurredAt: LocalDateTime,
    val description: String,
) {
}
