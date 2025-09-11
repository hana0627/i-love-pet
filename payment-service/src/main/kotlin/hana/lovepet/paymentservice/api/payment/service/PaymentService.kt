package hana.lovepet.paymentservice.api.payment.service

import hana.lovepet.paymentservice.api.payment.controller.dto.request.ConfirmPaymentRequest
import hana.lovepet.paymentservice.api.payment.controller.dto.request.FailPaymentRequest
import hana.lovepet.paymentservice.api.payment.controller.dto.request.PaymentCancelRequest
import hana.lovepet.paymentservice.api.payment.controller.dto.request.PreparePaymentRequest
import hana.lovepet.paymentservice.api.payment.controller.dto.request.PaymentRefundRequest
import hana.lovepet.paymentservice.api.payment.controller.dto.response.ConfirmPaymentResponse
import hana.lovepet.paymentservice.api.payment.controller.dto.response.GetPaymentLogResponse
import hana.lovepet.paymentservice.api.payment.controller.dto.response.PaymentCancelResponse
import hana.lovepet.paymentservice.api.payment.controller.dto.response.PreparePaymentResponse
import hana.lovepet.paymentservice.api.payment.controller.dto.response.PaymentRefundResponse
import hana.lovepet.paymentservice.api.payment.controller.dto.response.GetPaymentResponse
import hana.lovepet.paymentservice.infrastructure.kafka.`in`.dto.PaymentCancelEvent

interface PaymentService {
    fun preparePayment(userId: Long, orderId: Long, amount: Long, method: String): PreparePaymentResponse
//    fun confirmPayment(paymentId: Long, confirmPaymentRequest: ConfirmPaymentRequest): ConfirmPaymentResponse
    fun confirmPayment(orderId: Long, paymentId: Long, orderNo: String, paymentKey: String, amount: Long): ConfirmPaymentResponse
    fun getPayment(paymentId: Long): GetPaymentResponse
//    fun cancelPayment(paymentId: Long, paymentCancelRequest: PaymentCancelRequest): PaymentCancelResponse
    fun cancelPayment(paymentCancelEvent: PaymentCancelEvent): PaymentCancelResponse
    fun refundPayment(paymentId: Long, paymentRefundRequest: PaymentRefundRequest): PaymentRefundResponse
//    fun refundPayment(paymentCancelEvent: PaymentCancelEvent): PaymentRefundResponse
    fun getPaymentLogs(paymentId: Long): List<GetPaymentLogResponse>
//    fun failPayment(paymentId: Long, failPaymentRequest: FailPaymentRequest): Boolean
//    fun failPayment(paymentId: Long, failReason: String): Boolean
    fun failPayment(paymentId: Long, failReason: String): Boolean
    fun isPaymentCanceled(paymentId: Long): Boolean
}
