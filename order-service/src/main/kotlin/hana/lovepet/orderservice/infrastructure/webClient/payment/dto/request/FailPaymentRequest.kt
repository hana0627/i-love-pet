package hana.lovepet.orderservice.infrastructure.webClient.payment.dto.request

data class FailPaymentRequest(
    val orderId: Long,
    val paymentId: Long,
    val message: String,
    val code: String,
) {
}