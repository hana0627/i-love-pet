package hana.lovepet.paymentservice.api.payment.service

import hana.lovepet.paymentservice.api.payment.controller.dto.request.PaymentCancelRequest
import hana.lovepet.paymentservice.api.payment.controller.dto.request.PaymentCreateRequest
import hana.lovepet.paymentservice.api.payment.controller.dto.request.PaymentRefundRequest
import hana.lovepet.paymentservice.api.payment.controller.dto.response.PaymentCancelResponse
import hana.lovepet.paymentservice.api.payment.controller.dto.response.PaymentCreateResponse
import hana.lovepet.paymentservice.api.payment.controller.dto.response.PaymentRefundResponse
import hana.lovepet.paymentservice.api.payment.controller.dto.response.PaymentResponse

interface PaymentService {
    fun createPayment(paymentCreateRequest: PaymentCreateRequest): PaymentCreateResponse
    fun getPayment(paymentId: Long): PaymentResponse
    fun cancelPayment(paymentId: Long, paymentCancelRequest: PaymentCancelRequest): PaymentCancelResponse
    fun refundPayment(paymentId: Long, paymentRefundRequest: PaymentRefundRequest): PaymentRefundResponse
}
