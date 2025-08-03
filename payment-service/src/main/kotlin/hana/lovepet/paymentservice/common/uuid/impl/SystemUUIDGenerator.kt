package hana.lovepet.paymentservice.common.uuid.impl

import hana.lovepet.paymentservice.common.uuid.UUIDGenerator
import org.springframework.stereotype.Component
import java.util.*

@Component
class SystemUUIDGenerator : UUIDGenerator {
    override fun generate(): String {
        return UUID.randomUUID().toString()
    }
}
