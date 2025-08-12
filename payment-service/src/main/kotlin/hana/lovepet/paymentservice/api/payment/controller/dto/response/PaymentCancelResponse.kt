package hana.lovepet.paymentservice.api.payment.controller.dto.response

import java.time.LocalDateTime

data class PaymentCancelResponse(
    val paymentId: Long,
    val canceledAt: LocalDateTime? = null,
    val transactionKey: String? = null,
    val message: String? = null
) {
}
