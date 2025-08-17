package hana.lovepet.orderservice.api.service

import hana.lovepet.orderservice.api.controller.dto.request.CreateOrderRequest
import hana.lovepet.orderservice.api.controller.dto.request.OrderSearchCondition
import hana.lovepet.orderservice.api.controller.dto.response.GetOrderItemsResponse
import hana.lovepet.orderservice.api.controller.dto.response.GetOrdersResponse
import hana.lovepet.orderservice.api.controller.dto.response.OrderCreateResponse
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface OrderService {
    fun createOrder(createOrderRequest: CreateOrderRequest): OrderCreateResponse
    fun getOrders(orderSearchCondition: OrderSearchCondition, pageable: Pageable): Page<GetOrdersResponse>
    fun getOrderItems(orderNo: String): List<GetOrderItemsResponse>
}
