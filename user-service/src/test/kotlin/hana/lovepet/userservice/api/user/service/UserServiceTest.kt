package hana.lovepet.userservice.api.user.service

import hana.lovepet.userservice.api.user.controller.dto.request.UserRegisterRequest
import hana.lovepet.userservice.api.user.domain.User
import hana.lovepet.userservice.api.user.repository.UserRepository
import hana.lovepet.userservice.api.user.service.impl.UserServiceImpl
import hana.lovepet.userservice.common.clock.TimeProvider
import jakarta.persistence.EntityNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.then
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDateTime
import java.util.*


@ExtendWith(MockitoExtension::class)
class UserServiceTest {

    @Mock
    lateinit var userRepository: UserRepository
    @Mock
    lateinit var timeProvider: TimeProvider

    lateinit var userService: UserServiceImpl

    @BeforeEach fun setup() {
        userService = UserServiceImpl(userRepository, timeProvider)
    }

    @Test
    fun `회원가입에 성공한다`() {
        //given
        given(timeProvider.now()).willReturn(LocalDateTime.of(2025,7,26,9,0,0))

        val userRegisterRequest = UserRegisterRequest.fixture()
        val user = User(
            name = userRegisterRequest.name,
            email = userRegisterRequest.email,
            phoneNumber = userRegisterRequest.phoneNumber,
            createdAt = timeProvider.now(),
        ).apply { id = 1L }

        given(userRepository.save(any(User::class.java))).willReturn(user)

        //when
        val registeredUser = userService.registerUser(userRegisterRequest)

        //then
        then(userRepository).should().save(any(User::class.java))

        assertThat(user.id).isEqualTo(registeredUser.userId)
        assertThat(user.name).isEqualTo(registeredUser.name)
        assertThat(user.phoneNumber).isEqualTo(registeredUser.phoneNumber)
        assertThat(user.createdAt).isEqualTo(timeProvider.now())
    }

    @Test
    fun `회원조회에 성공한다`() {
        //given
        given(timeProvider.now()).willReturn(LocalDateTime.of(2025,7,26,9,0,0))

        val userId = 1L
        val user = User.fixture(timeProvider = timeProvider).apply { id = userId }


        given(userRepository.findById(userId)).willReturn(Optional.of(user))

        //when
        val foundUser = userService.getUserProfile(userId)

        //then
        then(userRepository).should().findById(userId)

        assertThat(foundUser.userId).isEqualTo(user.id)
        assertThat(foundUser.name).isEqualTo(user.name)
        assertThat(foundUser.email).isEqualTo(user.email)
        assertThat(foundUser.phoneNumber).isEqualTo(user.phoneNumber)
    }

    @Test
    fun `없는id로 회원 조회시 예외가 발생한다`() {
        //given
        val userId = 9999L

        given(userRepository.findById(userId)).willReturn(Optional.empty())

        //when
        val result = assertThrows<EntityNotFoundException> {userService.getUserProfile(userId)}

        //then
        then(userRepository).should().findById(userId)

        assertThat(result.message).isEqualTo("User not found [id = $userId]")
    }

    @Test
    fun `id로 회원이 존재하는지 여부를 확인한다(존재하는 경우 true)`() {
        //given
        given(timeProvider.now()).willReturn(LocalDateTime.of(2025,7,26,9,0,0))

        val userId = 1L
        val user = User.fixture(timeProvider = timeProvider).apply { id = userId }

        given(userRepository.findById(userId)).willReturn(Optional.of(user))

        //when
        val result = userService.isUserExists(userId)

        //then
        then(userRepository).should().findById(userId)

        assertThat(result.exist).isTrue()

    }

    @Test
    fun `id로 회원이 존재하는지 여부를 확인한다(존재하지 않는 경우 false)`() {

        //given
        val userId = 9999L

        given(userRepository.findById(userId)).willReturn(Optional.empty())

        //when
        val result = userService.isUserExists(userId)

        //then
        then(userRepository).should().findById(userId)

        assertThat(result.exist).isFalse()
    }

}

