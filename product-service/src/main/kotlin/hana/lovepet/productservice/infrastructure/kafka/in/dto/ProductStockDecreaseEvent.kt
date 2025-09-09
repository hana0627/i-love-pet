package hana.lovepet.productservice.infrastructure.kafka.`in`.dto

data class ProductStockDecreaseEvent (
    val eventId: String,
    val orderId: Long,
    val products: List<Product>,
    val idempotencyKey: String,
){
    data class Product(
        val productId: Long,
        val quantity: Int,
    )
}
