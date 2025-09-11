package hana.lovepet.paymentservice.infrastructure.kafka.out.dto

import java.time.LocalDateTime

data class PaymentCanceledEvent(
    val eventId: String,
    val cancelAt: LocalDateTime,
    val orderId: Long,
    val paymentId: Long,
    val idempotencyKey: String
)
