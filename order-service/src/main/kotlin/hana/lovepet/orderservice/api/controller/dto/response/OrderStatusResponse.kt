package hana.lovepet.orderservice.api.controller.dto.response

import hana.lovepet.orderservice.api.domain.constant.OrderStatus

data class OrderStatusResponse(
    val orderNo: String,
    val status: OrderStatus,
    val amount: Long?,
    val errorMessage: String?,
) {
}
