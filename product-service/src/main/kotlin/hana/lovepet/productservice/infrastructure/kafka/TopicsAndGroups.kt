package hana.lovepet.productservice.infrastructure.kafka

object Topics {
    const val PRODUCT_INFORMATION_REQUEST = "product.information.request"
    const val PRODUCT_INFORMATION_RESPONSE = "product.information.response"
    const val PRODUCT_STOCK_DECREASE = "product.stock.decrease"
    const val PRODUCT_STOCK_DECREASED = "product.stock.decreased"
    const val PRODUCT_STOCK_ROLLBACK = "product.stock.rollback"

}

object Groups {
    const val PRODUCT = "product-service"
}