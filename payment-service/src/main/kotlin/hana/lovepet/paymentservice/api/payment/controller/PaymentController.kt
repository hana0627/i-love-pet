package hana.lovepet.paymentservice.api.payment.controller

import hana.lovepet.paymentservice.api.payment.controller.dto.request.PaymentRefundRequest
import hana.lovepet.paymentservice.api.payment.controller.dto.response.GetPaymentLogResponse
import hana.lovepet.paymentservice.api.payment.controller.dto.response.GetPaymentResponse
import hana.lovepet.paymentservice.api.payment.controller.dto.response.PaymentRefundResponse
import hana.lovepet.paymentservice.api.payment.service.PaymentService
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
//    @PostMapping
//    fun preparePayment(@RequestBody preparePaymentRequest: PreparePaymentRequest): ResponseEntity<PreparePaymentResponse> {
//        println("PaymentController.preparePayment")
//        val response = paymentService.preparePayment(preparePaymentRequest)
//        println("response = ${response}")
//        return ResponseEntity.status(HttpStatus.CREATED).body(response)
//    }

//    @PatchMapping("/{paymentId}/confirm")
//    fun confirmPayment(
//        @PathVariable("paymentId") paymentId: Long,
//        @RequestBody confirmPaymentRequest: ConfirmPaymentRequest
//    ): ResponseEntity<ConfirmPaymentResponse> {
//        val response = paymentService.confirmPayment(paymentId, confirmPaymentRequest)
//        return ResponseEntity.status(HttpStatus.CREATED).body(response)
//    }

    /**
     * 결제 취소
     */
//    @PatchMapping("/{paymentId}/cancel")
//    fun cancelPayment(@PathVariable("paymentId") paymentId: Long, @RequestBody paymentCancelRequest: PaymentCancelRequest): ResponseEntity<PaymentCancelResponse> {
//        val response = paymentService.cancelPayment(paymentId, paymentCancelRequest)
//        return ResponseEntity.ok(response)
//    }


//    @PatchMapping("/{paymentId}/fail")
//    fun failPayment(
//        @PathVariable("paymentId") paymentId: Long,
//        @RequestBody failPaymentRequest: FailPaymentRequest
//    ): ResponseEntity<Boolean> {
//        val response = paymentService.failPayment(paymentId, failPaymentRequest)
//        return ResponseEntity.status(HttpStatus.OK).body(response)
//    }


    /**
     * 결제정보 조회
     */
    @GetMapping("/{paymentId}")
    fun getPayment(@PathVariable("paymentId") paymentId: Long): ResponseEntity<GetPaymentResponse> {
        val response = paymentService.getPayment(paymentId)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/{paymentId}/logs")
    fun getPaymentLogs(@PathVariable("paymentId") paymentId: Long): ResponseEntity<List<GetPaymentLogResponse>> {
        val response = paymentService.getPaymentLogs(paymentId)
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