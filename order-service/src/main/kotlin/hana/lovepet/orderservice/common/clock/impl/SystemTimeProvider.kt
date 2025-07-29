package hana.lovepet.orderservice.common.clock.impl

import hana.lovepet.orderservice.common.clock.TimeProvider
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class SystemTimeProvider : TimeProvider {
    override fun now(): LocalDateTime {
        return LocalDateTime.now()
    }
}
