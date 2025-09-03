package hana.lovepet.productservice.infrastructure.kafka.`in`.dto

data class GetProductsEvent(
    val eventId: String,
    val orderId: Long,
    val items: List<OrderItemRequest>,
    val idempotencyKey: String,
) {

    data class OrderItemRequest(
        val productId: Long,
        val quantity: Int
    ) {
    }
}
