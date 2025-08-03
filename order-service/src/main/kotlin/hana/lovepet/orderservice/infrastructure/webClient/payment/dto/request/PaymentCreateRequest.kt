package hana.lovepet.orderservice.infrastructure.webClient.payment.dto.request

data class PaymentCreateRequest(
    val userId: Long,
    val orderId: Long,
    val amount: Long,
    val method: String? = null
) {

    companion object {
        fun fixture(
            userId: Long = 1L,
            orderId: Long = 1000L,
            amount: Long = 5000L,
            method: String? = "카드"
        ): PaymentCreateRequest {
            return PaymentCreateRequest(
                userId = userId,
                orderId = orderId,
                amount = amount,
                method = method,
            )
        }

    }
}
