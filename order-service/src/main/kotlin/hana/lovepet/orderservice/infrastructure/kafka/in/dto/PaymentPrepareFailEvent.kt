package hana.lovepet.orderservice.infrastructure.kafka.`in`.dto

data class PaymentPrepareFailEvent(
    val eventId: String,
    val orderId: Long,
    val idempotencyKey: String
)
