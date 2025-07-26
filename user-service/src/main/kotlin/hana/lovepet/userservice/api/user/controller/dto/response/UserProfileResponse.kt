package hana.lovepet.userservice.api.user.controller.dto.response

data class UserProfileResponse(
    val userId: Long,
    val name: String,
    val email: String,
    val phoneNumber: String,
) {
    companion object {
        fun fixture(
            userId: Long = 1L,
            name: String = "박하나",
            email: String = "hanana@lovepet.com",
            phoneNumber: String = "01036066270",
        ): UserProfileResponse {
            return UserProfileResponse(
                userId = userId,
                name = name,
                email = email,
                phoneNumber = phoneNumber,
            )
        }
    }
}
