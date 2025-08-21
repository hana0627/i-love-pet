package hana.lovepet.orderservice.api.controller.dto.request

data class ConfirmOrderRequest(
    val paymentKey: String,
    val orderId: String,
    val amount: Long
) {
}
