package hana.lovepet.productservice.api.product.controller.dto.response

data class ProductInformationResponse(
    val name: String,
    val price: Long,
    val stock: Int
) {

    companion object {
        fun fixture(
            name: String = "로얄캐닌 고양이 사료",
            price: Long = 35000L,
            stock: Int = 1000,
        ): ProductInformationResponse {
            return ProductInformationResponse(
                name = name,
                price = price,
                stock = stock
            )
        }
    }
}
