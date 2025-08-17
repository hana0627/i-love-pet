package hana.lovepet.paymentservice.api.payment.controller.dto.response

import hana.lovepet.paymentservice.api.payment.domain.constant.LogType
import java.time.LocalDateTime

data class GetPaymentLogResponse(
    val logType: LogType,
    val message: String,
    val createdAt: LocalDateTime
){
}
