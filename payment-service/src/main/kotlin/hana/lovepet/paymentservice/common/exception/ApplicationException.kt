package hana.lovepet.paymentservice.common.exception

import hana.lovepet.paymentservice.common.exception.constant.ErrorCode

data class ApplicationException(
    val errorCode: ErrorCode,
    override val message: String? = null,
) : RuntimeException() {

    val getMessage: String
        get() =
            message ?: errorCode.message
}
