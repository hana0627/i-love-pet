package hana.lovepet.orderservice.infrastructure.webClient.user.dto

data class UserExistResponse(
    val userId: Long,
    val userName: String,
) {
}
