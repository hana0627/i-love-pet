package hana.lovepet.userservice.common.clock

import java.time.LocalDateTime

interface TimeProvider {
    fun now(): LocalDateTime
}