package hana.lovepet.orderservice.infrastructure.webClient.payment.dto.request

data class ConfirmPaymentRequest(
    val orderId: Long,
    val orderNo: String,
    val paymentId: Long,
    val paymentKey: String,
    val amount: Long,
) {

}
