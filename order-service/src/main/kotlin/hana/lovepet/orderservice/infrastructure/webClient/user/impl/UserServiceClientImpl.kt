package hana.lovepet.orderservice.infrastructure.webClient.user.impl

import hana.lovepet.orderservice.common.exception.ApplicationException
import hana.lovepet.orderservice.common.exception.constant.ErrorCode
import hana.lovepet.orderservice.infrastructure.webClient.user.UserServiceClient
import hana.lovepet.orderservice.infrastructure.webClient.user.dto.UserExistResponse
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class UserServiceClientImpl(
    builder: WebClient.Builder,
) : UserServiceClient {

    private val webClient = builder
        .baseUrl("http://user-service:8080")
        .build()

    override fun getUser(userId: Long): UserExistResponse {
        return webClient.get()
            .uri("/api/users/$userId/exists")
            .retrieve()
            .bodyToMono(UserExistResponse::class.java)
            .block()
            ?: throw ApplicationException(ErrorCode.UNHEALTHY_SERVER_COMMUNICATION, "error occurred while retrieving user exists [id : $userId]")
    }
}
