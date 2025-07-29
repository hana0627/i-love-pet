package hana.lovepet.orderservice.api.controller.dto.request

data class OrderItemRequest(
    val productId: Long,
    val price: Long,
    val quantity: Int,
) {
}
