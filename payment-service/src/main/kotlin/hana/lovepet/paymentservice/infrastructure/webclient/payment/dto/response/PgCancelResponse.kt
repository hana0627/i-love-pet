package hana.lovepet.paymentservice.infrastructure.webclient.payment.dto.response

import java.time.LocalDateTime

sealed class PgCancelResponse {
    abstract val paymentKey: String

    data class Success(
        override val paymentKey: String,
        val transactionKey: String,
        val cancelAt: LocalDateTime,
    ) : PgCancelResponse()

    data class Fail(
        override val paymentKey: String,
        val code: String,
        val message: String,
    ) : PgCancelResponse()

    fun isSuccess(): Boolean {
        return this is Success
    }

    companion object {
        fun success(paymentKey: String, transactionKey: String, cancelAt: LocalDateTime) =
            Success(paymentKey, transactionKey, cancelAt)

        fun fail(paymentKey: String, code: String, message: String) =
            Fail(paymentKey, code, message)
    }

}
