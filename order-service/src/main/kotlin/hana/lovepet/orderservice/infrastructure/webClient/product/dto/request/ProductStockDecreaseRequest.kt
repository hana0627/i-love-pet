package hana.lovepet.orderservice.infrastructure.webClient.product.dto.request

data class ProductStockDecreaseRequest (
    val productId: Long,
    val quantity: Int,
){
}
