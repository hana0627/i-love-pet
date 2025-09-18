package hana.lovepet.paymentservice.infrastructure.webclient.payment.dto.response

data class TossPaymentCancelResponse(
    val paymentKey: String,
    val orderId: String,
    val status: String, // "CANCELED"
    val totalAmount: Long,
    val balanceAmount: Long, // 남은 금액
    val cancels: List<CancelDetail>,
    val canceledAt: String?
) {


    data class CancelDetail(
        val transactionKey: String,
        val cancelReason: String,
        val canceledAt: String,
        val cancelAmount: Long,
        val cancelStatus: String // "DONE"
    ){

    }
}


