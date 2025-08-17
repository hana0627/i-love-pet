package hana.lovepet.orderservice.api.controller.dto.response

data class GetOrderItemsResponse(
//    val orderItemId: Long,
    val productId: Long,
    val productName: String,
    val quantity: Int,
    val unitPrice: Long,
    val lineTotal: Long,
) {
}
