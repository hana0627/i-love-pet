package hana.lovepet.productservice.common.clock

import java.time.LocalDateTime

interface TimeProvider {
    fun now(): LocalDateTime
}