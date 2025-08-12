package hana.lovepet.orderservice.infrastructure.webClient.payment.dto.response

data class PaymentCancelResponse (
    val paymentId: Long,
    val paymentKey: String,
    val isSuccess: Boolean = true,
    val failReason: String? = null
){
}
