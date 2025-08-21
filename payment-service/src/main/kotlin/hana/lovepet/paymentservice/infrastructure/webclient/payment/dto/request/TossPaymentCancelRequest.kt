package hana.lovepet.paymentservice.infrastructure.webclient.payment.dto.request

data class TossPaymentCancelRequest(
    val cancelReason: String,
    val cancelAmount: Long? = null
) {
}
