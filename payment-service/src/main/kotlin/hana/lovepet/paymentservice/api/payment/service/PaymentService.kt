package hana.lovepet.paymentservice.api.payment.service

import hana.lovepet.paymentservice.api.payment.controller.dto.request.PaymentCancelRequest
import hana.lovepet.paymentservice.api.payment.controller.dto.request.PaymentCreateRequest
import hana.lovepet.paymentservice.api.payment.controller.dto.request.PaymentRefundRequest
import hana.lovepet.paymentservice.api.payment.controller.dto.response.GetPaymentLogResponse
import hana.lovepet.paymentservice.api.payment.controller.dto.response.PaymentCancelResponse
import hana.lovepet.paymentservice.api.payment.controller.dto.response.PaymentCreateResponse
import hana.lovepet.paymentservice.api.payment.controller.dto.response.PaymentRefundResponse
import hana.lovepet.paymentservice.api.payment.controller.dto.response.GetPaymentResponse

interface PaymentService {
    fun createPayment(paymentCreateRequest: PaymentCreateRequest): PaymentCreateResponse
    fun getPayment(paymentId: Long): GetPaymentResponse
    fun cancelPayment(paymentId: Long, paymentCancelRequest: PaymentCancelRequest): PaymentCancelResponse
    fun refundPayment(paymentId: Long, paymentRefundRequest: PaymentRefundRequest): PaymentRefundResponse
    fun getPaymentLogs(paymentId: Long): List<GetPaymentLogResponse>
}
