package hana.lovepet.orderservice.api.controller.dto.response

import hana.lovepet.orderservice.api.domain.constant.OrderStatus
import java.time.LocalDateTime

data class GetOrdersResponse(
    val orderId: Long,
    val orderNo: String,
    val userId: Long,
    val userName: String? = null,
    val status: OrderStatus,
    val price: Long,
    val createdAt: LocalDateTime,
    val paymentId: Long? = null,
    ) {

}

