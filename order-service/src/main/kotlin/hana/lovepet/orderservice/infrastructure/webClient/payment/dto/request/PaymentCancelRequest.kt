package hana.lovepet.orderservice.infrastructure.webClient.payment.dto.request

data class PaymentCancelRequest(
    val refundReason: String,
) {

    companion object {
        fun fixture(
            refundReason: String = "단순변심",
        ): PaymentCancelRequest {
            return PaymentCancelRequest(
                refundReason = refundReason,
            )
        }

    }
}
