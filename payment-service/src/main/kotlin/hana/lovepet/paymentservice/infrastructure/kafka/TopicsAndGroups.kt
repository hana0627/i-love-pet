package hana.lovepet.paymentservice.infrastructure.kafka

object Topics {
    const val PAYMENT_PREPARE = "payment.prepare"
    const val PAYMENT_PREPARED = "payment.prepared"
    const val PAYMENT_PREPARE_FAIL = "payment.prepared.fail"
}

object Groups {
    const val PAYMENT = "payment-service"
}