package hana.lovepet.paymentservice.infrastructure.kafka

object Topics {
    const val PAYMENT_PREPARE = "payment.prepare"
    const val PAYMENT_PREPARED = "payment.prepared"
    const val PAYMENT_PREPARE_FAIL = "payment.prepared.fail"

    const val PAYMENT_PENDING = "payment.pending"
    const val PAYMENT_CANCEL = "payment.cancel"

    const val PAYMENT_CONFIRMED = "payment.confirmed"
    const val PAYMENT_CONFIRMED_FAIL = "payment.confirmed.fail"

    const val PAYMENT_CANCELED = "payment.canceled"
    const val PAYMENT_CANCELED_FAIL = "payment.canceled.fail"
}

object Groups {
    const val PAYMENT = "payment-service"
}