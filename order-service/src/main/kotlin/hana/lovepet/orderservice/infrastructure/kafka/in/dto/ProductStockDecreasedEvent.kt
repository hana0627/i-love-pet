package hana.lovepet.orderservice.infrastructure.kafka.`in`.dto

data class ProductStockDecreasedEvent (
    val eventId: String,
    val orderId: Long,
    val success: Boolean,
    val errorMessage: String? = null,
    val idempotencyKey: String,
){
}
