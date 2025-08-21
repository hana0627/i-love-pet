package hana.lovepet.orderservice.infrastructure.webClient.payment

import hana.lovepet.orderservice.infrastructure.webClient.payment.dto.request.ConfirmPaymentRequest
import hana.lovepet.orderservice.infrastructure.webClient.payment.dto.request.FailPaymentRequest
import hana.lovepet.orderservice.infrastructure.webClient.payment.dto.request.PaymentCancelRequest
import hana.lovepet.orderservice.infrastructure.webClient.payment.dto.request.PreparePaymentRequest
import hana.lovepet.orderservice.infrastructure.webClient.payment.dto.response.ConfirmPaymentResponse
import hana.lovepet.orderservice.infrastructure.webClient.payment.dto.response.PaymentCancelResponse
import hana.lovepet.orderservice.infrastructure.webClient.payment.dto.response.PreparePaymentResponse

interface PaymentServiceClient {
    fun prepare(preparePaymentRequest: PreparePaymentRequest): PreparePaymentResponse
    fun confirm(confirmPaymentRequest: ConfirmPaymentRequest): ConfirmPaymentResponse
    fun approve(preparePaymentRequest: PreparePaymentRequest): PreparePaymentResponse
    fun cancel(paymentId: Long, paymentCancelRequest: PaymentCancelRequest): PaymentCancelResponse
    fun fail(failPaymentRequest: FailPaymentRequest): Boolean
}
