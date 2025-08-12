package hana.lovepet.orderservice.infrastructure.webClient.payment.impl

import hana.lovepet.orderservice.common.exception.ApplicationException
import hana.lovepet.orderservice.common.exception.constant.ErrorCode
import hana.lovepet.orderservice.infrastructure.webClient.payment.PaymentServiceClient
import hana.lovepet.orderservice.infrastructure.webClient.payment.dto.request.PaymentCancelRequest
import hana.lovepet.orderservice.infrastructure.webClient.payment.dto.request.PaymentCreateRequest
import hana.lovepet.orderservice.infrastructure.webClient.payment.dto.response.PaymentCancelResponse
import hana.lovepet.orderservice.infrastructure.webClient.payment.dto.response.PaymentCreateResponse
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class PaymentServiceClientImpl(
    builder: WebClient.Builder
) : PaymentServiceClient {

    private val webClient = builder
        .baseUrl("http://payment-service:8080")
        .build()

    override fun approve(paymentCreateRequest: PaymentCreateRequest): PaymentCreateResponse {

        return try {
            webClient.post()
                .uri("/api/payments")
                .contentType(APPLICATION_JSON)
                .bodyValue(paymentCreateRequest)
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
                .bodyToMono(PaymentCreateResponse::class.java)
                .block() ?: throw ApplicationException(ErrorCode.PAYMENTS_FAIL, "예상하지 못한 결제응답")
        } catch (e: Exception) {
            throw ApplicationException(ErrorCode.UNHEALTHY_SERVER_COMMUNICATION, "error occurred while communicate payment-service [orderId : ${paymentCreateRequest.orderId}]")
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
                .block() ?: throw ApplicationException(ErrorCode.PAYMENTS_FAIL, "예상하지 못한 결제응답")
        } catch (e: Exception) {
            throw ApplicationException(ErrorCode.UNHEALTHY_SERVER_COMMUNICATION, "error occurred while communicate payment-service [paymentId : $paymentId]")
        }
    }
}
