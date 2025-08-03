package hana.lovepet.paymentservice.api.payment.controller.dto

import hana.lovepet.paymentservice.api.payment.controller.dto.request.PaymentCancelRequest
import hana.lovepet.paymentservice.api.payment.controller.dto.request.PaymentCreateRequest
import hana.lovepet.paymentservice.api.payment.controller.dto.request.PaymentRefundRequest
import hana.lovepet.paymentservice.api.payment.controller.dto.response.PaymentCancelResponse
import hana.lovepet.paymentservice.api.payment.controller.dto.response.PaymentCreateResponse
import hana.lovepet.paymentservice.api.payment.controller.dto.response.PaymentRefundResponse
import hana.lovepet.paymentservice.api.payment.controller.dto.response.PaymentResponse
import hana.lovepet.paymentservice.api.payment.service.PaymentService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/payments")
class PaymentController(
    private val paymentService: PaymentService
) {

    /**
     * 결제 진행
     */
    @PostMapping
    fun createPayment(@RequestBody paymentCreateRequest: PaymentCreateRequest): ResponseEntity<PaymentCreateResponse> {
        val response = paymentService.createPayment(paymentCreateRequest)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    /**
     * 결제정보 조회
     */
    @GetMapping("/{paymentId}")
    fun getPayment(@PathVariable("paymentId") paymentId: Long): ResponseEntity<PaymentResponse> {
        val response = paymentService.getPayment(paymentId)
        return ResponseEntity.ok(response)
    }

    /**
     * 결제 취소
     */
    @PatchMapping("/{paymentId}/cancel")
    fun cancelPayment(@PathVariable("paymentId") paymentId: Long, @RequestBody paymentCancelRequest: PaymentCancelRequest): ResponseEntity<PaymentCancelResponse> {
        val response = paymentService.cancelPayment(paymentId, paymentCancelRequest)
        return ResponseEntity.ok(response)
    }

    /**
     * 결제 환불
     */
    @PatchMapping("/{paymentId}/refund")
    fun refundPayment(@PathVariable("paymentId") paymentId: Long, @RequestBody paymentRefundRequest: PaymentRefundRequest): ResponseEntity<PaymentRefundResponse> {
        val response = paymentService.refundPayment(paymentId, paymentRefundRequest)
        return ResponseEntity.ok(response)
    }
}

