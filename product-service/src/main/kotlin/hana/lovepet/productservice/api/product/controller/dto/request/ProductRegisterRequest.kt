package hana.lovepet.productservice.api.product.controller.dto.request

data class ProductRegisterRequest(
    val productName: String,
    val price: Long,
    val stock: Int
) {

    companion object {
        fun fixture(
            productName: String = "로얄캐닌 고양이 사료",
            price: Long = 35000L,
            stock: Int = 1000,
        ): ProductRegisterRequest {
            return ProductRegisterRequest(
                productName = productName,
                price = price,
                stock = stock
            )
        }
    }
}
