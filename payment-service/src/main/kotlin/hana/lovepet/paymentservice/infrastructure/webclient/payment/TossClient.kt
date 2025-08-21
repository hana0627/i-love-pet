package hana.lovepet.paymentservice.infrastructure.webclient.payment

import hana.lovepet.paymentservice.infrastructure.webclient.payment.dto.request.TossPaymentConfirmRequest
import hana.lovepet.paymentservice.infrastructure.webclient.payment.dto.response.TossPaymentCancelResponse
//import hana.lovepet.paymentservice.infrastructure.webclient.payment.dto.response.PgCancelResponse
import hana.lovepet.paymentservice.infrastructure.webclient.payment.dto.response.TossPaymentConfirmResponse

interface TossClient {
    //    fun approve(request: PgApproveRequest): PgApproveResponse
//    fun cancel(paymentKey: String, cancelReason: String): PgCancelResponse
    fun confirm(tossPaymentConfirmRequest: TossPaymentConfirmRequest): TossPaymentConfirmResponse
    fun cancel(paymentKey: String, cancelReason: String): TossPaymentCancelResponse
}
