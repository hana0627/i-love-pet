package hana.lovepet.paymentservice.infrastructure.kafka.out.dto

import java.time.LocalDateTime

data class PaymentConfirmedEvent(
    val eventId: String,
    val occurredAt: LocalDateTime,
    val orderId: Long,
    val paymentId: Long,
    val idempotencyKey: String
)
