package hana.lovepet.orderservice.api.controller.dto.request

// TODO api update
data class CreateOrderItemRequest(
    val productId: Long,
    val productName: String,
    val price: Long,
    val quantity: Int,
) {
}
