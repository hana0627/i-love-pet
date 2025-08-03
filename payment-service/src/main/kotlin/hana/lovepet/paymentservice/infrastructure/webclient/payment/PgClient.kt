package hana.lovepet.paymentservice.infrastructure.webclient.payment

import hana.lovepet.paymentservice.infrastructure.webclient.payment.dto.request.PgApproveRequest
import hana.lovepet.paymentservice.infrastructure.webclient.payment.dto.response.PgApproveResponse

interface PgClient {
    fun approve(request: PgApproveRequest): PgApproveResponse
}
