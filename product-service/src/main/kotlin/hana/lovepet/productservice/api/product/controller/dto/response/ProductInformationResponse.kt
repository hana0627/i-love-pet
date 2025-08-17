package hana.lovepet.productservice.api.product.controller.dto.response

//TODO api update
data class ProductInformationResponse(
    val productId: Long,
    val productName: String,
    val price: Long,
    val stock: Int,
) {

    companion object {
        fun fixture(
            productId: Long = 1L,
            productName: String = "로얄캐닌 고양이 사료",
            price: Long = 35000L,
            stock: Int = 1000,
        ): ProductInformationResponse {
            return ProductInformationResponse(
                productId = productId,
                productName = productName,
                price = price,
                stock = stock
            )
        }
    }
}
