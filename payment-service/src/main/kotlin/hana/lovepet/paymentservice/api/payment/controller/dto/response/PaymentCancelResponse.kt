package hana.lovepet.paymentservice.api.payment.controller.dto.response

data class PaymentCancelResponse(
    val paymentId: Long,
    val paymentKey: String,
) {
}
