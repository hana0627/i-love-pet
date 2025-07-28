package hana.lovepet.productservice.common.clock.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

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

