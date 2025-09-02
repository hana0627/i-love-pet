package hana.lovepet.orderservice.infrastructure.kafka

object Topics {
    const val PAYMENT_PREPARE = "payment.prepare"
    const val PAYMENT_PREPARED = "payment.prepared"
}

object Groups {
    const val ORDER = "order-service"
    const val PAYMENT = "payment-service"
}