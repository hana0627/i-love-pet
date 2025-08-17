package hana.lovepet.userservice.api.user.controller.dto.request

data class UserRegisterRequest(
    val userName: String,
    val email: String,
    val phoneNumber: String
) {

    companion object {
        fun fixture(
            userName: String = "박하나",
            email: String = "test@lovepet@lovepet.com",
            phoneNumber: String = "01036066270"
        ) : UserRegisterRequest {
            return UserRegisterRequest(
                userName = userName,
                email = email,
                phoneNumber = phoneNumber
            )
        }
    }
}
