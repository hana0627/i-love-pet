package hana.lovepet.orderservice.infrastructure.kafka.`in`.dto

data class PaymentCanceledFailEvent(
    val eventId: String,
    val orderId: Long,
    val idempotencyKey: String
)
