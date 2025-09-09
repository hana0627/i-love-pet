package hana.lovepet.orderservice.infrastructure.kafka.out.dto

data class PaymentCancelEvent(
    val eventId: String,
    val orderId: Long,
    val paymentKey: String,
    val idempotencyKey: String
) {
}