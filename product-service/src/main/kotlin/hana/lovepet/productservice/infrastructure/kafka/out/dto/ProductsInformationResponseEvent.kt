package hana.lovepet.productservice.infrastructure.kafka.out.dto

data class ProductsInformationResponseEvent(
    val eventId: String,
    val orderId: Long,
    val success: Boolean,
    val products: List<ProductInformationResponse> = emptyList(),
    val errorMessage: String? = null
) {
    data class ProductInformationResponse(
        val productId: Long,
        val productName: String,
        val price: Long,
        val stock: Int,
        val quantity: Int // 주문 수량도 포함해서 응답받기
    )
}
