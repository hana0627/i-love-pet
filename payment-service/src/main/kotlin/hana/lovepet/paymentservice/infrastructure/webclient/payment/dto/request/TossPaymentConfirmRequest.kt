package hana.lovepet.paymentservice.infrastructure.webclient.payment.dto.request

data class TossPaymentConfirmRequest(
    val orderId: String,
    val amount: Long,
    val paymentKey: String
) {
}
