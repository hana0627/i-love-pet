package hana.lovepet.paymentservice.api.payment.controller.dto.response

data class PaymentRefundResponse(
    val paymentId: Long,
    val paymentKey: String,
) {
}
