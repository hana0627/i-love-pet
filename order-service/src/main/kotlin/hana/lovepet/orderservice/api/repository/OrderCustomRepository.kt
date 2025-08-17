package hana.lovepet.orderservice.api.repository

import hana.lovepet.orderservice.api.controller.dto.request.OrderSearchCondition
import hana.lovepet.orderservice.api.controller.dto.response.GetOrdersResponse
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface OrderCustomRepository {

    fun searchOrders(orderSearchCondition: OrderSearchCondition, pageable: Pageable): Page<GetOrdersResponse>

}
