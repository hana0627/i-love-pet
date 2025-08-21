package hana.lovepet.orderservice.api.controller.dto.request

data class FailOrderRequest(
    val orderId: String,
    val code: String,
    val message: String
) {
}
