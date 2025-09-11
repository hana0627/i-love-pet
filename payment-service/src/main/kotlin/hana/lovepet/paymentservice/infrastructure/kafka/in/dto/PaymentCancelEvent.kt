package hana.lovepet.paymentservice.infrastructure.kafka.`in`.dto

data class PaymentCancelEvent(
    val eventId: String,
    val orderId: Long,
    val orderNo: String,
    val paymentId: Long,
    val refundReason: String,
    val idempotencyKey: String
) {
}