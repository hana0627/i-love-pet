package hana.lovepet.paymentservice.infrastructure.webclient.payment.impl

import hana.lovepet.orderservice.common.exception.constant.ErrorCode
import hana.lovepet.paymentservice.common.exception.ApplicationException
import hana.lovepet.paymentservice.infrastructure.webclient.payment.TossClient
import hana.lovepet.paymentservice.infrastructure.webclient.payment.dto.request.TossPaymentCancelRequest
import hana.lovepet.paymentservice.infrastructure.webclient.payment.dto.request.TossPaymentConfirmRequest
import hana.lovepet.paymentservice.infrastructure.webclient.payment.dto.response.TossPaymentCancelResponse
//import hana.lovepet.paymentservice.infrastructure.webclient.payment.dto.response.PgCancelResponse
import hana.lovepet.paymentservice.infrastructure.webclient.payment.dto.response.TossPaymentConfirmResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.nio.charset.StandardCharsets
import java.util.*

@Component
class TossClientImpl(
    builder: WebClient.Builder
) :  TossClient{

    val log: Logger = LoggerFactory.getLogger(TossClientImpl::class.java)
    private val secretKey: String = "test_gsk_docs_OaPz8L5KdmQXkzRz3y47BMw6"

    private val webClient = builder.
        baseUrl("https://api.tosspayments.com")
        .build()

    override fun confirm(tossPaymentConfirmRequest: TossPaymentConfirmRequest): TossPaymentConfirmResponse {
        val auth = Base64.getEncoder().encodeToString("$secretKey:".toByteArray(StandardCharsets.UTF_8))

        return try {
            webClient.post()
                .uri("/v1/payments/confirm")
                .header("Authorization", "Basic $auth")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(tossPaymentConfirmRequest)
                .retrieve()
                .onStatus({ status -> status.is4xxClientError }) { res ->
                    res.bodyToMono(String::class.java).map { errorBody ->
                        log.error("토스페이먼츠 결제 승인 4xx 에러: $errorBody")
                        throw ApplicationException(ErrorCode.UNHEALTHY_PG_COMMUNICATION, "결제 승인 실패: $errorBody")
                    }
                }
                .onStatus({ status -> status.is5xxServerError }) { res ->
                    res.bodyToMono(String::class.java).map { errorBody ->
                        log.error("토스페이먼츠 결제 승인 5xx 에러: $errorBody")
                        throw ApplicationException(ErrorCode.UNHEALTHY_PG_COMMUNICATION, "결제 서비스 장애: $errorBody")
                    }
                }
                .bodyToMono(TossPaymentConfirmResponse::class.java)
                .block() ?: throw ApplicationException(ErrorCode.UNHEALTHY_PG_COMMUNICATION, "결제 승인 응답이 없습니다.")

        } catch (e: ApplicationException) {
            throw e
        } catch (e: Exception) {
            log.error("토스페이먼츠 결제 승인 중 예외 발생", e)
            throw ApplicationException(ErrorCode.UNHEALTHY_PG_COMMUNICATION, "결제 승인 중 오류가 발생했습니다.")
        }
    }

    override fun cancel(paymentKey: String, cancelReason: String): TossPaymentCancelResponse {
        val auth = Base64.getEncoder().encodeToString("$secretKey:".toByteArray(StandardCharsets.UTF_8))
        val cancelRequest = TossPaymentCancelRequest(cancelReason = cancelReason)

        log.info("토스페이먼츠 결제 취소 요청: paymentKey=$paymentKey, reason=$cancelReason")


        return try {
            val response = webClient.post()
                .uri("/v1/payments/$paymentKey/cancel")
                .header("Authorization", "Basic $auth")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(cancelRequest)
                .retrieve()
                .onStatus({ status -> status.is4xxClientError }) { res ->
                    res.bodyToMono(String::class.java).map { errorBody ->
                        log.error("토스페이먼츠 결제 취소 4xx 에러: paymentKey=$paymentKey, error=$errorBody")
                        throw ApplicationException(ErrorCode.UNHEALTHY_PG_COMMUNICATION, "결제 취소 실패: $errorBody")
                    }
                }
                .onStatus({ status -> status.is5xxServerError }) { res ->
                    res.bodyToMono(String::class.java).map { errorBody ->
                        log.error("토스페이먼츠 결제 취소 5xx 에러: paymentKey=$paymentKey, error=$errorBody")
                        throw ApplicationException(ErrorCode.UNHEALTHY_PG_COMMUNICATION, "결제 서비스 장애: $errorBody")
                    }
                }
                .bodyToMono(TossPaymentCancelResponse::class.java)
                .block() ?: throw ApplicationException(ErrorCode.UNHEALTHY_PG_COMMUNICATION, "결제 취소 응답이 없습니다.")

            log.info("토스페이먼츠 결제 취소 성공: paymentKey=$paymentKey, status=${response.status}")
            response


        } catch (e: ApplicationException) {
            throw e
        } catch (e: Exception) {
            log.error("토스페이먼츠 결제 취소 실패: paymentKey=$paymentKey, reason=$cancelReason", e)
            throw ApplicationException(ErrorCode.UNHEALTHY_PG_COMMUNICATION, "결제 취소 중 오류가 발생했습니다.")
        }

    }


}