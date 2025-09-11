package hana.lovepet.orderservice.infrastructure.kafka.`in`.dto

import java.time.LocalDateTime

data class PaymentConfirmedEvent(
    val eventId: String,
    val occurredAt: LocalDateTime,
    val orderId: Long,
    val paymentId: Long,
    val idempotencyKey: String
)
