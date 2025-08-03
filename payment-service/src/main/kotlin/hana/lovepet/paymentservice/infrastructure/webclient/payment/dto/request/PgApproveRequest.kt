package hana.lovepet.paymentservice.infrastructure.webclient.payment.dto.request

data class PgApproveRequest(
    val orderId: Long,
    val userId: Long,
    val amount: Long,
    val method: String?
) {

}
