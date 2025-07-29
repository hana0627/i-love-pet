package hana.lovepet.orderservice.infrastructure.webClient.product.dto

data class ProductInfoResponse (
    val productId: Long,
    val price: Long,
    val stock: Int,
){
}
