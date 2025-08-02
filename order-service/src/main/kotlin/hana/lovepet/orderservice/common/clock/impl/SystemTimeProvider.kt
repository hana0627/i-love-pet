package hana.lovepet.orderservice.common.clock.impl

import hana.lovepet.orderservice.common.clock.TimeProvider
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Component
class SystemTimeProvider : TimeProvider {
    override fun now(): LocalDateTime {
        return LocalDateTime.now()
    }

    override fun todayString(): String {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
    }
}
