package hana.lovepet.orderservice.infrastructure.webClient.payment.impl

import hana.lovepet.orderservice.common.exception.ApplicationException
import hana.lovepet.orderservice.common.exception.constant.ErrorCode
import hana.lovepet.orderservice.infrastructure.webClient.payment.PaymentServiceClient
import hana.lovepet.orderservice.infrastructure.webClient.payment.dto.request.ConfirmPaymentRequest
import hana.lovepet.orderservice.infrastructure.webClient.payment.dto.request.FailPaymentRequest
import hana.lovepet.orderservice.infrastructure.webClient.payment.dto.request.PaymentCancelRequest
import hana.lovepet.orderservice.infrastructure.webClient.payment.dto.request.PreparePaymentRequest
import hana.lovepet.orderservice.infrastructure.webClient.payment.dto.response.ConfirmPaymentResponse
import hana.lovepet.orderservice.infrastructure.webClient.payment.dto.response.PaymentCancelResponse
import hana.lovepet.orderservice.infrastructure.webClient.payment.dto.response.PreparePaymentResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration

@Component
class PaymentServiceClientImpl(
    builder: WebClient.Builder
) : PaymentServiceClient {

    val log: Logger = LoggerFactory.getLogger(PaymentServiceClientImpl::class.java)

    private val webClient = builder
        .baseUrl("http://payment-service:8080")
        .build()



    override fun prepare(preparePaymentRequest: PreparePaymentRequest): PreparePaymentResponse {
        return try {
            webClient.post()
                .uri("/api/payments")
                .contentType(APPLICATION_JSON)
                .bodyValue(preparePaymentRequest)
                .retrieve()
                .onStatus({ status -> status.is4xxClientError }) { res ->
                    res.bodyToMono(String::class.java).map {
                        throw ApplicationException(ErrorCode.PAYMENTS_REQUEST_FAIL, "결제 요청 실패: $it")
                    }
                }
                .onStatus({ status -> status.is5xxServerError }) { res ->
                    res.bodyToMono(String::class.java).map {
                        throw ApplicationException(ErrorCode.PAYMENTS_FAIL, "결제 서비스 장애: $it")
                    }
                }
                .bodyToMono(PreparePaymentResponse::class.java)
                .block() ?: throw ApplicationException(ErrorCode.PAYMENTS_FAIL, "예상하지 못한 결제응답")
        } catch (e: Exception) {
            log.error(e.message, e)
            throw ApplicationException(ErrorCode.UNHEALTHY_SERVER_COMMUNICATION, "error occurred while communicate payment-service [orderId : ${preparePaymentRequest.orderId}]")
        }
    }

    override fun confirm(confirmPaymentRequest: ConfirmPaymentRequest): ConfirmPaymentResponse {
        return try {
            webClient.patch()
                .uri("/api/payments/${confirmPaymentRequest.paymentId}/confirm")
                .contentType(APPLICATION_JSON)
                .bodyValue(confirmPaymentRequest)
                .retrieve()
                .onStatus({ status -> status.is4xxClientError }) { res ->
                    res.bodyToMono(String::class.java).map {
                        throw ApplicationException(ErrorCode.PAYMENTS_REQUEST_FAIL, "결제 확정 요청 실패: $it")
                    }
                }
                .onStatus({ status -> status.is5xxServerError }) { res ->
                    res.bodyToMono(String::class.java).map {
                        throw ApplicationException(ErrorCode.PAYMENTS_FAIL, "결제 서비스 장애: $it")
                    }
                }
                .bodyToMono(ConfirmPaymentResponse::class.java)
                .block() ?: throw ApplicationException(ErrorCode.PAYMENTS_FAIL, "예상하지 못한 결제응답")
        } catch (e: Exception) {
            log.error(e.message, e)
            throw ApplicationException(ErrorCode.UNHEALTHY_SERVER_COMMUNICATION, "error occurred while communicate payment-service [orderId : ${confirmPaymentRequest.orderId}]")
        }
    }


    override fun approve(preparePaymentRequest: PreparePaymentRequest): PreparePaymentResponse {

        return try {
            webClient.post()
                .uri("/api/payments")
                .contentType(APPLICATION_JSON)
                .bodyValue(preparePaymentRequest)
                .retrieve()
                .onStatus({ status -> status.is4xxClientError }) { res ->
                    res.bodyToMono(String::class.java).map {
                        throw ApplicationException(ErrorCode.PAYMENTS_REQUEST_FAIL, "결제 요청 실패: $it")
                    }
                }
                .onStatus({ status -> status.is5xxServerError }) { res ->
                    res.bodyToMono(String::class.java).map {
                        throw ApplicationException(ErrorCode.PAYMENTS_FAIL, "결제 서비스 장애: $it")
                    }
                }
                .bodyToMono(PreparePaymentResponse::class.java)
                .block() ?: throw ApplicationException(ErrorCode.PAYMENTS_FAIL, "예상하지 못한 결제응답")
        } catch (e: Exception) {
            log.error(e.message, e)
            throw ApplicationException(ErrorCode.UNHEALTHY_SERVER_COMMUNICATION, "error occurred while communicate payment-service [orderId : ${preparePaymentRequest.orderId}]")
        }

    }

    override fun cancel(paymentId: Long, paymentCancelRequest: PaymentCancelRequest): PaymentCancelResponse {
        return try {
            webClient.patch()
                .uri("/api/payments/$paymentId/cancel")
                .contentType(APPLICATION_JSON)
                .bodyValue(paymentCancelRequest)
                .retrieve()
                .onStatus({ status -> status.is4xxClientError }) { res ->
                    res.bodyToMono(String::class.java).map {
                        throw ApplicationException(ErrorCode.PAYMENTS_REQUEST_FAIL, "결제 취소 요청 실패: $it")
                    }
                }
                .onStatus({ status -> status.is5xxServerError }) { res ->
                    res.bodyToMono(String::class.java).map {
                        throw ApplicationException(ErrorCode.PAYMENTS_FAIL, "결제 서비스 장애: $it")
                    }
                }
                .bodyToMono(PaymentCancelResponse::class.java)
                .timeout(Duration.ofSeconds(30))  // 타임아웃 명시적 설정
                .block() ?: throw ApplicationException(ErrorCode.PAYMENTS_FAIL, "예상하지 못한 결제응답")
        } catch (e: Exception) {
            throw ApplicationException(ErrorCode.UNHEALTHY_SERVER_COMMUNICATION, "error occurred while communicate payment-service [paymentId : $paymentId]")
        }
    }

    override fun fail(failPaymentRequest: FailPaymentRequest): Boolean {
        return try {
            webClient.patch()
                .uri("/api/payments/${failPaymentRequest.paymentId}/fail")
                .contentType(APPLICATION_JSON)
                .bodyValue(failPaymentRequest)
                .retrieve()
                .onStatus({ status -> status.is4xxClientError }) { res ->
                    res.bodyToMono(String::class.java).map {
                        throw ApplicationException(ErrorCode.PAYMENTS_REQUEST_FAIL, "결제 취소 요청 실패: $it")
                    }
                }
                .onStatus({ status -> status.is5xxServerError }) { res ->
                    res.bodyToMono(String::class.java).map {
                        throw ApplicationException(ErrorCode.PAYMENTS_FAIL, "결제 서비스 장애: $it")
                    }
                }
                .bodyToMono(Boolean::class.java)
                .block() ?: throw ApplicationException(ErrorCode.PAYMENTS_FAIL, "예상하지 못한 결제응답")
        } catch (e: Exception) {
            throw ApplicationException(ErrorCode.UNHEALTHY_SERVER_COMMUNICATION, "error occurred while communicate payment-service [paymentId : ${failPaymentRequest.paymentId}]")
        }
    }
}
