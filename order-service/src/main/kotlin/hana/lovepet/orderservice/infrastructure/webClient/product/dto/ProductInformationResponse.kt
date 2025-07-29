package hana.lovepet.orderservice.infrastructure.webClient.product.dto

data class ProductInformationResponse (
    val productId: Long,
    val name: String,
    val price: Long,
    val stock: Int,
){
}
