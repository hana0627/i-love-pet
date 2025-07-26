package hana.lovepet.userservice.api.user.service.impl

import hana.lovepet.userservice.api.user.controller.dto.request.UserRegisterRequest
import hana.lovepet.userservice.api.user.controller.dto.response.UserExistResponse
import hana.lovepet.userservice.api.user.controller.dto.response.UserRegisterResponse
import hana.lovepet.userservice.api.user.controller.dto.response.UserProfileResponse
import hana.lovepet.userservice.api.user.domain.User
import hana.lovepet.userservice.api.user.repository.UserRepository
import hana.lovepet.userservice.api.user.service.UserService
import hana.lovepet.userservice.common.clock.TimeProvider
import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import java.util.*

@Service
class UserServiceImpl(
    private val userRepository: UserRepository,
    private val timeProvider: TimeProvider
) : UserService {
    override fun registerUser(userRegisterRequest: UserRegisterRequest): UserRegisterResponse {
        val user = User(
            name = userRegisterRequest.name,
            email = userRegisterRequest.email,
            phoneNumber = userRegisterRequest.phoneNumber,
            createdAt = timeProvider.now()
        )

        val savedUser = userRepository.save(user)

        return UserRegisterResponse(
            userId = savedUser.id!!,
            name = savedUser.name,
            email = savedUser.email,
            phoneNumber = savedUser.phoneNumber,
            )
    }

    override fun getUserProfile(userId: Long): UserProfileResponse{
        val user = userRepository.findById(userId).orElseThrow { EntityNotFoundException("User not found [id = $userId]") }
        return UserProfileResponse(
            userId = user.id!!,
            name = user.name,
            email = user.email,
            phoneNumber = user.phoneNumber,
        )
    }

    override fun isUserExists(id: Long): UserExistResponse {
        val user: Optional<User> = userRepository.findById(id)
        return UserExistResponse(exist = user.isPresent)
    }

}
