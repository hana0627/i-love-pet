package hana.lovepet.userservice.common.clock.impl

import org.assertj.core.api.Assertions.assertThat
import java.time.LocalDateTime
import kotlin.test.Test

class SystemTimeProviderTest {

    private val timeProvider = SystemTimeProvider()

    @Test
    fun `now는 현재 시간을 반환한다`() {
        // when
        val now = timeProvider.now()

        // then
        assertThat(now).isNotNull()
        assertThat(now).isBefore(LocalDateTime.now().plusSeconds(1))
        assertThat(now).isAfter(LocalDateTime.now().minusSeconds(1))
    }
}
