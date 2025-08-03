package hana.lovepet.orderservice.infrastructure.webClient.payment.impl

import hana.lovepet.orderservice.infrastructure.webClient.payment.PaymentServiceClient
import hana.lovepet.orderservice.infrastructure.webClient.payment.dto.request.PaymentCreateRequest
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
                        throw IllegalArgumentException("결제 요청 실패: $it")
                    }
                }
                .onStatus({ status -> status.is5xxServerError }) { res ->
                    res.bodyToMono(String::class.java).map {
                        throw RuntimeException("결제 서비스 장애: $it")
                    }
                }
                .bodyToMono(PaymentCreateResponse::class.java)
                .block() ?: throw IllegalStateException("결제 응답이 null입니다")
        } catch (e: Exception) {
            throw RuntimeException("error occurred while communicate payment-service [orderId : ${paymentCreateRequest.orderId}]")
        }

    }
}
