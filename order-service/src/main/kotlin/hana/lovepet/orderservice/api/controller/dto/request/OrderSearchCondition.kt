package hana.lovepet.orderservice.api.controller.dto.request

import hana.lovepet.orderservice.api.domain.constant.OrderStatus

data class OrderSearchCondition(
    val userId: Long?,
    val status: OrderStatus?,
    val searchQuery: String?,
) {
}
