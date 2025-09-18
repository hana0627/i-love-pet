package hana.lovepet.paymentservice.api.payment.service

import hana.lovepet.paymentservice.api.payment.controller.dto.request.PaymentRefundRequest
import hana.lovepet.paymentservice.api.payment.controller.dto.response.*
import hana.lovepet.paymentservice.api.payment.controller.dto.response.PaymentRefundResponse
import hana.lovepet.paymentservice.infrastructure.kafka.`in`.dto.PaymentCancelEvent

interface PaymentService {
    fun preparePayment(userId: Long, orderId: Long, amount: Long, method: String): PreparePaymentResponse
    fun confirmPayment(orderId: Long, paymentId: Long, orderNo: String, paymentKey: String, amount: Long): ConfirmPaymentResponse
    fun getPayment(paymentId: Long): GetPaymentResponse
    fun cancelPayment(paymentCancelEvent: PaymentCancelEvent): PaymentCancelResponse
    fun refundPayment(paymentId: Long, paymentRefundRequest: PaymentRefundRequest): PaymentRefundResponse
    fun getPaymentLogs(paymentId: Long): List<GetPaymentLogResponse>
    fun failPayment(paymentId: Long, failReason: String): Boolean
    fun isPaymentCanceled(paymentId: Long): Boolean
}
