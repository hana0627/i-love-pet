package hana.lovepet.userservice.common.clock.impl

import hana.lovepet.userservice.common.clock.TimeProvider
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class SystemTimeProvider : TimeProvider {
    override fun now(): LocalDateTime {
        return LocalDateTime.now()
    }
}
