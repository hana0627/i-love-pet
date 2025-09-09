package hana.lovepet.productservice.infrastructure.kafka.out.dto

data class ProductStockDecreasedEvent (
    val eventId: String,
    val orderId: Long,
    val success: Boolean,
    val errorMessage: String? = null,
    val idempotencyKey: String,
){
}
