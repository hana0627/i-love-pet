package hana.lovepet.paymentservice.infrastructure.webclient.payment.dto.response

data class TossPaymentConfirmResponse(
    val paymentKey: String,
    val orderId: String,
    val status: String,
    val totalAmount: Long,
    val method: String?,
    val requestedAt: String?,
    val approvedAt: String?
) {
}
