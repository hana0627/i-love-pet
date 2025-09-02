package hana.lovepet.orderservice.infrastructure.kafka.out.dto

data class OrderCreateEvent(
    val eventId: String,
    val occurredAt: String,
    val orderNo: String,
    val userId: Long,
    val items: List<Item>,
    val totalAmount: Long,
    val idempotencyKey: String

) {
    data class Item(val productId: Long, val quantity: Int, val price: Long)
}