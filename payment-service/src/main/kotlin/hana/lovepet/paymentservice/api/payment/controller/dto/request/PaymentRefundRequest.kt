package hana.lovepet.paymentservice.api.payment.controller.dto.request

data class PaymentRefundRequest(
    val cancelReason: String,
) {
}
