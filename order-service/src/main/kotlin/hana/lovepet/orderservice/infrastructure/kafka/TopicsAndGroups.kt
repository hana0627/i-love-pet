package hana.lovepet.orderservice.infrastructure.kafka

object Topics {
    const val PAYMENT_PREPARE = "payment.prepare"
    const val PAYMENT_PREPARED = "payment.prepared"
    const val PAYMENT_PREPARE_FAIL = "payment.prepared.fail"
    const val PRODUCT_INFORMATION_REQUEST = "product.information.request"
    const val PRODUCT_INFORMATION_RESPONSE = "product.information.response"
    const val PRODUCT_STOCK_DECREASE = "product.stock.decrease"
    const val PRODUCT_STOCK_DECREASED = "product.stock.decreased"
    const val PAYMENT_PENDING = "payment.pending"
    const val PAYMENT_CANCEL = "payment.cancel"
    const val PRODUCT_STOCK_ROLLBACK = "product.stock.rollback"
}

object Groups {
    const val ORDER = "order-service"
}