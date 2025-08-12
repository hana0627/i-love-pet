package hana.lovepet.paymentservice.infrastructure.webclient.payment.dto.request

data class PgCancelRequest(
    val paymentKey: String,
    val cancelReason: String,
) {

}
