package hana.lovepet.userservice.api.user.domain

import hana.lovepet.userservice.common.clock.TimeProvider
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDateTime


@ExtendWith(MockitoExtension::class)
class UserTest {

    @Mock
    lateinit var timeProvider: TimeProvider

    @BeforeEach
    fun setUp() {
        given(timeProvider.now()).willReturn(LocalDateTime.of(2025,7,26,9,0,0))
    }

    @Test
    fun `userfixture를 통해 테스용 유저새성이 가능하다`() {
        //given
        val name = "박하나"
        val email = "hanana@lovepet.com"
        val phoneNumber = "01036066270"

        //when
        val user = User.fixture(timeProvider = timeProvider)

        //then
        Assertions.assertThat(user.name).isEqualTo(name)
        Assertions.assertThat(user.email).isEqualTo(email)
        Assertions.assertThat(user.phoneNumber).isEqualTo(phoneNumber)
        Assertions.assertThat(user.createdAt).isEqualTo(timeProvider.now())

    }
}