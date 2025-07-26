package hana.lovepet.userservice.api.user.controller.dto.request

data class UserRegisterRequest(
    val name: String,
    val email: String,
    val phoneNumber: String
) {

    companion object {
        fun fixture(
            name: String = "박하나",
            email: String = "test@lovepet@lovepet.com",
            phoneNumber: String = "01036066270"
        ) : UserRegisterRequest {
            return UserRegisterRequest(
                name = name,
                email = email,
                phoneNumber = phoneNumber
            )
        }
    }
}
