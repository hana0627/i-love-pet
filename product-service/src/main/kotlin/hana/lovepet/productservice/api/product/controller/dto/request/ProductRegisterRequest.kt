package hana.lovepet.productservice.api.product.controller.dto.request

data class ProductRegisterRequest(
    val name: String,
    val price: Long,
    val stock: Int
) {

    companion object {
        fun fixture(
            name: String = "로얄캐닌 고양이 사료",
            price: Long = 35000L,
            stock: Int = 1000,
        ): ProductRegisterRequest {
            return ProductRegisterRequest(
                name = name,
                price = price,
                stock = stock
            )
        }
    }
}
