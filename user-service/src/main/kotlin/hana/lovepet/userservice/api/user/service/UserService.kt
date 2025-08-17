package hana.lovepet.userservice.api.user.service

import hana.lovepet.userservice.api.user.controller.dto.request.UserRegisterRequest
import hana.lovepet.userservice.api.user.controller.dto.response.AllUserResponse
import hana.lovepet.userservice.api.user.controller.dto.response.UserExistResponse
import hana.lovepet.userservice.api.user.controller.dto.response.UserRegisterResponse
import hana.lovepet.userservice.api.user.controller.dto.response.UserProfileResponse

interface UserService {
    fun registerUser(userRegisterRequest: UserRegisterRequest): UserRegisterResponse
    fun getUserProfile(userId: Long): UserProfileResponse
    fun isUserExists(userId: Long): UserExistResponse
    fun getAllUsers(): List<AllUserResponse>
}
