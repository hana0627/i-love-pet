package hana.lovepet.orderservice.common.clock

import java.time.LocalDateTime

interface TimeProvider {
    fun now(): LocalDateTime
    fun todayString(): String
}
