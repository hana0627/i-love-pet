package hana.lovepet.orderservice.infrastructure.kafka.out.dto

data class PaymentPendingEvent(
    val eventId: String,
    val orderId: Long,
    val paymentKey: String,
    val amount: Long,
    val idempotencyKey: String
) {
}