package hana.lovepet.orderservice.infrastructure.webClient.payment.dto.request

data class PreparePaymentRequest(
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
        ): PreparePaymentRequest {
            return PreparePaymentRequest(
                userId = userId,
                orderId = orderId,
                amount = amount,
                method = method,
            )
        }

    }
}
