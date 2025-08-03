package hana.lovepet.paymentservice.common.clock

import java.time.LocalDateTime

interface TimeProvider {
    fun now(): LocalDateTime
}