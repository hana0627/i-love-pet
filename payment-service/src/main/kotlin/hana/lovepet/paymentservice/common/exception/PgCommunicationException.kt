package hana.lovepet.paymentservice.common.exception


class PgCommunicationException(message: String?, cause: Throwable? = null) : RuntimeException(message, cause) {
}
