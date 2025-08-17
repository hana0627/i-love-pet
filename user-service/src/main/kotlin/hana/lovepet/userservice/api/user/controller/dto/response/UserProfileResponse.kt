package hana.lovepet.userservice.api.user.controller.dto.response

data class UserProfileResponse(
    val userId: Long,
    val userName: String,
    val email: String,
    val phoneNumber: String,
) {
    companion object {
        fun fixture(
            userId: Long = 1L,
            userName: String = "박하나",
            email: String = "hanana@lovepet.com",
            phoneNumber: String = "01036066270",
        ): UserProfileResponse {
            return UserProfileResponse(
                userId = userId,
                userName = userName,
                email = email,
                phoneNumber = phoneNumber,
            )
        }
    }
}
