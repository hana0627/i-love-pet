package hana.lovepet.orderservice.api.controller

import hana.lovepet.orderservice.api.controller.dto.request.OrderCreateRequest
import hana.lovepet.orderservice.api.controller.dto.response.OrderCreateResponse
import hana.lovepet.orderservice.api.service.OrderService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/orders")
class OrderController (
    private val orderService: OrderService,
){
    @PostMapping
    fun createOrder(@RequestBody orderCreateRequest: OrderCreateRequest): ResponseEntity<OrderCreateResponse> {
        val response = orderService.createOrder(orderCreateRequest)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

}
