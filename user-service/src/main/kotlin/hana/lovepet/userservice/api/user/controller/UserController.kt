package hana.lovepet.userservice.api.user.controller

import hana.lovepet.userservice.api.user.controller.dto.request.UserRegisterRequest
import hana.lovepet.userservice.api.user.controller.dto.response.UserExistResponse
import hana.lovepet.userservice.api.user.controller.dto.response.UserRegisterResponse
import hana.lovepet.userservice.api.user.controller.dto.response.UserProfileResponse
import hana.lovepet.userservice.api.user.service.UserService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
@RequestMapping("/api/users")
class UserController (
    private val userService: UserService
){

    @PostMapping
    fun registerUser(@RequestBody userRegisterRequest: UserRegisterRequest): ResponseEntity<UserRegisterResponse>{
        val response = userService.registerUser(userRegisterRequest)
        return ResponseEntity
            .created(URI.create("/api/users/${response.userId}"))
            .body(response)
    }

    @GetMapping("/{userId}")
    fun getUserProfile(@PathVariable("userId") userId:Long): ResponseEntity<UserProfileResponse> {
        val result = userService.getUserProfile(userId)
        return ResponseEntity.ok(result)
    }

    @GetMapping("/{userId}/exist")
    fun existUser(@PathVariable("userId") userId:Long): ResponseEntity<UserExistResponse> {
        val result = userService.isUserExists(userId)
        return ResponseEntity.ok(result)
    }

}
