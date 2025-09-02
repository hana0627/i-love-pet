package hana.lovepet.orderservice.infrastructure.kafka.out.dto

data class PaymentPrepareEvent(
    val eventId: String,
    val occurredAt: String,
    val orderId: Long,
    val userId: Long,
    val amount: Long,
    val method: String? = "카드",
    val idempotencyKey: String,
) {
}