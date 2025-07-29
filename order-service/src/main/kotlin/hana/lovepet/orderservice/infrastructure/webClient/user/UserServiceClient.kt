package hana.lovepet.orderservice.infrastructure.webClient.user

import hana.lovepet.orderservice.infrastructure.webClient.user.dto.UserExistResponse

interface UserServiceClient {
    fun getUser(userId: Long): UserExistResponse
}
