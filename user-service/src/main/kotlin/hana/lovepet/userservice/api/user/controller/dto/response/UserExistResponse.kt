package hana.lovepet.userservice.api.user.controller.dto.response

data class UserExistResponse(
    val userId: Long,
    val userName: String,
) {
}
