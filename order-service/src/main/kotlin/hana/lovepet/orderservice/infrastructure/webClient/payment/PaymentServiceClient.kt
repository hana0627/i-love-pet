package hana.lovepet.orderservice.infrastructure.webClient.payment

import hana.lovepet.orderservice.infrastructure.webClient.payment.dto.request.PaymentCreateRequest
import hana.lovepet.orderservice.infrastructure.webClient.payment.dto.response.PaymentCreateResponse

interface PaymentServiceClient {
    fun approve(paymentCreateRequest: PaymentCreateRequest): PaymentCreateResponse
}
