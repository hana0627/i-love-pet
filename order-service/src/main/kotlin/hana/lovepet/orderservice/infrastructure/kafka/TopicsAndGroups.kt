package hana.lovepet.orderservice.infrastructure.kafka

object Topics {
    const val PAYMENT_PREPARE = "payment.prepare"
    const val PAYMENT_PREPARED = "payment.prepared"
    const val PAYMENT_PREPARE_FAIL = "payment.prepared.fail"
    const val PRODUCT_INFORMATION_REQUEST = "product.information.request"
    const val PRODUCT_INFORMATION_RESPONSE = "product.information.response"
}

object Groups {
    const val ORDER = "order-service"
}