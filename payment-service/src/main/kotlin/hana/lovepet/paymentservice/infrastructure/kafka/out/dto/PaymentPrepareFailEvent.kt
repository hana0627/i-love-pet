package hana.lovepet.paymentservice.infrastructure.kafka.out.dto

data class PaymentPrepareFailEvent(
    val eventId: String,
    val orderId: Long,
    val idempotencyKey: String
)
