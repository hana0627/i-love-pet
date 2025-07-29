package hana.lovepet.orderservice.api.service

import hana.lovepet.orderservice.api.controller.dto.request.OrderCreateRequest
import hana.lovepet.orderservice.api.controller.dto.response.OrderCreateResponse

interface OrderService {
    fun createOrder(orderCreateRequest: OrderCreateRequest): OrderCreateResponse
}
