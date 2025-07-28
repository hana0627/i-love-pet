package hana.lovepet.productservice.common.clock.impl

import hana.lovepet.productservice.common.clock.TimeProvider
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class SystemTimeProvider : TimeProvider {
    override fun now(): LocalDateTime {
        return LocalDateTime.now()
    }
}
