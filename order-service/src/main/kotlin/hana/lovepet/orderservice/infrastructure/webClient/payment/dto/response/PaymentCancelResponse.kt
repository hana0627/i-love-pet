package hana.lovepet.orderservice.infrastructure.webClient.payment.dto.response

import java.time.LocalDateTime

data class PaymentCancelResponse (
    val paymentId: Long,
    val canceledAt: LocalDateTime? = null,
    val transactionKey: String? = null,
    val message: String? = null
){
}
