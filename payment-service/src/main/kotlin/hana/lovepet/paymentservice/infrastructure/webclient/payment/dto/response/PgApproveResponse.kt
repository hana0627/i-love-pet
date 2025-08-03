package hana.lovepet.paymentservice.infrastructure.webclient.payment.dto.response

sealed class PgApproveResponse {
    abstract val paymentKey: String
    abstract val rawJson: String

    data class Success(
        override val paymentKey: String,
        val amount: Long,
        val method: String,
        override val rawJson: String
    ) : PgApproveResponse()

    data class Fail(
        override val paymentKey: String,
        val failReason: String,
        override val rawJson: String
    ) : PgApproveResponse()

    fun isSuccess(): Boolean {
        return this is Success
    }

    companion object {
        fun success(paymentKey: String, amount: Long, method: String, rawJson: String) =
            Success(paymentKey, amount, method, rawJson)

        fun fail(paymentKey: String, failReason: String, rawJson: String) =
            Fail(paymentKey, failReason, rawJson)
    }

}
