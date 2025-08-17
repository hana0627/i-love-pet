package hana.lovepet.orderservice.api.controller

import hana.lovepet.orderservice.api.controller.dto.request.CreateOrderRequest
import hana.lovepet.orderservice.api.controller.dto.request.OrderSearchCondition
import hana.lovepet.orderservice.api.controller.dto.response.GetOrderItemsResponse
import hana.lovepet.orderservice.api.controller.dto.response.GetOrdersResponse
import hana.lovepet.orderservice.api.controller.dto.response.OrderCreateResponse
import hana.lovepet.orderservice.api.service.OrderService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/orders")
class OrderController (
    private val orderService: OrderService,
){
    @PostMapping
    fun createOrder(@RequestBody createOrderRequest: CreateOrderRequest): ResponseEntity<OrderCreateResponse> {
        val response = orderService.createOrder(createOrderRequest)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }


    @GetMapping
    fun getOrders(
        @ModelAttribute orderSearchCondition: OrderSearchCondition,
        @PageableDefault(size = 20) pageable: Pageable
    ): ResponseEntity<Page<GetOrdersResponse>> {
        val response = orderService.getOrders(orderSearchCondition, pageable)
        return ResponseEntity.status(HttpStatus.OK).body(response)
    }

    @GetMapping("/{orderNo}/items")
    fun getOrderItems(@PathVariable(name = "orderNo") orderNo: String): ResponseEntity<List<GetOrderItemsResponse>>{
        val response = orderService.getOrderItems(orderNo)
        return ResponseEntity.status(HttpStatus.OK).body(response)
    }


}
