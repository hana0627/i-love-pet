package hana.lovepet.userservice.api.user.controller

import com.fasterxml.jackson.databind.ObjectMapper
import hana.lovepet.userservice.api.user.controller.dto.request.UserRegisterRequest
import hana.lovepet.userservice.api.user.controller.dto.response.UserExistResponse
import hana.lovepet.userservice.api.user.controller.dto.response.UserProfileResponse
import hana.lovepet.userservice.api.user.controller.dto.response.UserRegisterResponse
import hana.lovepet.userservice.api.user.service.UserService
import hana.lovepet.userservice.common.exception.RestControllerHandler
import jakarta.persistence.EntityNotFoundException
import org.hamcrest.CoreMatchers.equalTo
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@WebMvcTest(UserController::class)
@Import(RestControllerHandler::class)
class UserControllerTest {

    @Autowired
    lateinit var mvc: MockMvc

    @Autowired
    lateinit var om: ObjectMapper

    @MockitoBean
    lateinit var userService: UserService


    @Test
    fun `회원가입에 성공하면 201을 응답한다`() {
        //given
        val userRegisterRequest = UserRegisterRequest.fixture()
        val userRegisterResponse = UserRegisterResponse.fixture(userRegisterRequest)
        given(userService.registerUser(userRegisterRequest)).willReturn(userRegisterResponse)

        val json = om.writeValueAsString(userRegisterRequest)

        //when & then
        mvc.post("/api/users"){
            contentType = MediaType.APPLICATION_JSON
            content = json
        }
            .andExpect { status { isCreated() }
                jsonPath("$.userId", equalTo(userRegisterResponse.userId.toInt()))
                jsonPath("$.userName", equalTo(userRegisterResponse.userName))
                jsonPath("$.email", equalTo(userRegisterResponse.email))
                jsonPath("$.phoneNumber", equalTo(userRegisterResponse.phoneNumber))
            }
            .andDo { print() }
    }

    @Test
    fun `회원 단건 조회에 성공한다`() {
        //given
        val userProfileResponse = UserProfileResponse.fixture()
        val userId = 1L

        given(userService.getUserProfile(userId)).willReturn(userProfileResponse)

        //when & then
        mvc.get("/api/users/{userId}", userId)
            .andExpect { status { isOk() }
                jsonPath("$.userId", equalTo(userProfileResponse.userId.toInt()))
                jsonPath("$.userName", equalTo(userProfileResponse.userName))
                jsonPath("$.email", equalTo(userProfileResponse.email))
                jsonPath("$.phoneNumber", equalTo(userProfileResponse.phoneNumber))
            }
            .andDo { print() }

    }

    @Test
    fun `없는 Id로 조회시 회원 단건 조회에 실패한다`() {
        //given
        val userId = 9999L

        given(userService.getUserProfile(userId)).willThrow(EntityNotFoundException("User not found [id = $userId]"))

        //when & then
        mvc.get("/api/users/{userId}", userId)
            .andExpect { status { isNotFound() }
                status { isNotFound() }
                jsonPath("$.message", equalTo("User not found [id = $userId]"))
            }
            .andDo { print() }

    }


    @Test
    fun `회원존재여부를 확인한다 유저가 있는경우`() {
        //given
        val userId = 1L
        val userName = "박하나"
        val userExistResponse = UserExistResponse(userId = userId, userName = userName)

        given(userService.isUserExists(userId)).willReturn(userExistResponse)

        //when & then
        mvc.get("/api/users/{userId}/exists", userId)
            .andExpect { status { isOk() }
                jsonPath("$.userId", equalTo(userId.toInt()))
                jsonPath("$.userName", equalTo(userName))
            }
            .andDo { print() }

    }


    @Test
    fun `회원존재여부를 확인한다 유저가 없는경우`() {
        //given
        val userId = 9999L
        val userName = "없는유저"

        given(userService.isUserExists(userId)).willThrow(EntityNotFoundException("User not found [id = $userId]"))

        //when & then
        mvc.get("/api/users/{userId}/exists", userId)
            .andExpect { status { isNotFound() }
                jsonPath("$.message", equalTo("User not found [id = $userId]"))
            }
            .andDo { print() }

    }


}
