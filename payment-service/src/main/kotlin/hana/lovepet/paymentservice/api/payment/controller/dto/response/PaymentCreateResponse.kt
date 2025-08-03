package hana.lovepet.paymentservice.api.payment.controller.dto.response

data class PaymentCreateResponse (
    val paymentId: Long,
    val paymentKey: String,
    val failReason: String? = null
){
}
