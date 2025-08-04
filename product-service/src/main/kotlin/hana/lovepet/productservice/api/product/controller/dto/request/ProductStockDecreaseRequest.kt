package hana.lovepet.productservice.api.product.controller.dto.request

data class ProductStockDecreaseRequest (
    val productId: Long,
    val quantity: Int,
){
}
