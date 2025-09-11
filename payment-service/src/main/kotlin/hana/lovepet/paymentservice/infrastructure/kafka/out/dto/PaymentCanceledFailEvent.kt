package hana.lovepet.paymentservice.infrastructure.kafka.out.dto

data class PaymentCanceledFailEvent(
    val eventId: String,
    val orderId: Long,
    val idempotencyKey: String
)
