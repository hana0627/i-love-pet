package hana.lovepet.orderservice.infrastructure.kafka.out.dto

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

