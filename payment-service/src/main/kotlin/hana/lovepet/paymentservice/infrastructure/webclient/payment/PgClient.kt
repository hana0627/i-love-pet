package hana.lovepet.paymentservice.infrastructure.webclient.payment

import hana.lovepet.paymentservice.infrastructure.webclient.payment.dto.request.PgApproveRequest
import hana.lovepet.paymentservice.infrastructure.webclient.payment.dto.response.PgApproveResponse
import hana.lovepet.paymentservice.infrastructure.webclient.payment.dto.response.PgCancelResponse

interface PgClient {
    fun approve(request: PgApproveRequest): PgApproveResponse
    fun cancel(paymentKey: String, cancelReason: String): PgCancelResponse
}
