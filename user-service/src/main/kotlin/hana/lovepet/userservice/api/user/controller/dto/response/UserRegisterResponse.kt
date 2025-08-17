package hana.lovepet.userservice.api.user.controller.dto.response

import hana.lovepet.userservice.api.user.controller.dto.request.UserRegisterRequest

data class UserRegisterResponse (
    val userId: Long,
    val userName: String,
    val email: String,
    val phoneNumber: String,
){
    companion object {
        fun fixture(userRegisterRequest: UserRegisterRequest): UserRegisterResponse {
            return UserRegisterResponse(
                userId = 1L,
                userName = userRegisterRequest.userName,
                email = userRegisterRequest.email,
                phoneNumber = userRegisterRequest.phoneNumber,
            )
        }
    }
}
