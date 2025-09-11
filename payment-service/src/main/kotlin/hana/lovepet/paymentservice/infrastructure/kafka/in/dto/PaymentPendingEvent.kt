package hana.lovepet.paymentservice.infrastructure.kafka.`in`.dto

data class PaymentPendingEvent(
    val eventId: String,
    val orderId: Long,
    val paymentId: Long,
    val orderNo: String,
    val paymentKey: String,
    val amount: Long,
    val idempotencyKey: String
) {
}
