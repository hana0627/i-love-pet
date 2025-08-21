package hana.lovepet.orderservice.api.service

import hana.lovepet.orderservice.api.controller.dto.request.CreateOrderRequest
import hana.lovepet.orderservice.api.controller.dto.request.OrderSearchCondition
import hana.lovepet.orderservice.api.controller.dto.request.ConfirmOrderRequest
import hana.lovepet.orderservice.api.controller.dto.request.FailOrderRequest
import hana.lovepet.orderservice.api.controller.dto.response.GetOrderItemsResponse
import hana.lovepet.orderservice.api.controller.dto.response.GetOrdersResponse
import hana.lovepet.orderservice.api.controller.dto.response.ConfirmOrderResponse
import hana.lovepet.orderservice.api.controller.dto.response.PrepareOrderResponse
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface OrderService {
    fun prepareOrder(createOrderRequest: CreateOrderRequest): PrepareOrderResponse
//    fun createOrder(createOrderRequest: CreateOrderRequest): OrderCreateResponse
    fun getOrders(orderSearchCondition: OrderSearchCondition, pageable: Pageable): Page<GetOrdersResponse>
    fun getOrderItems(orderNo: String): List<GetOrderItemsResponse>
    fun confirmOrder(confirmOrderResponse: ConfirmOrderRequest): ConfirmOrderResponse
    fun failOrder(failOrderRequest: FailOrderRequest): Boolean
}
