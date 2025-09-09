package hana.lovepet.orderservice.api.controller

import hana.lovepet.orderservice.api.controller.dto.request.CreateOrderRequest
import hana.lovepet.orderservice.api.controller.dto.request.OrderSearchCondition
import hana.lovepet.orderservice.api.controller.dto.request.ConfirmOrderRequest
import hana.lovepet.orderservice.api.controller.dto.request.FailOrderRequest
import hana.lovepet.orderservice.api.controller.dto.response.GetOrderItemsResponse
import hana.lovepet.orderservice.api.controller.dto.response.GetOrdersResponse
import hana.lovepet.orderservice.api.controller.dto.response.ConfirmOrderResponse
import hana.lovepet.orderservice.api.controller.dto.response.OrderStatusResponse
import hana.lovepet.orderservice.api.controller.dto.response.PrepareOrderResponse
import hana.lovepet.orderservice.api.domain.constant.OrderStatus
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

    @PostMapping("/prepare")
    fun prepareOrder(@RequestBody createOrderRequest: CreateOrderRequest): ResponseEntity<PrepareOrderResponse> {
        val response = orderService.prepareOrder(createOrderRequest)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping("/{orderNo}/status")
    fun getOrderStatus(@PathVariable orderNo: String): ResponseEntity<OrderStatusResponse> {
        val response = orderService.getStatus(orderNo)
        return ResponseEntity.status(HttpStatus.OK).body(response)
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


    @PatchMapping("/confirm")
    fun confirmOrder(@RequestBody confirmOrderRequest: ConfirmOrderRequest): ResponseEntity<ConfirmOrderResponse> {
        val response = orderService.confirmOrder(confirmOrderRequest)
        return if (response.success) ResponseEntity.ok(response)
        else ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response)
    }

//    @PatchMapping("/fail")
//    fun failOrder(@RequestBody failOrderRequest: FailOrderRequest): ResponseEntity<Boolean> {
//        val response = orderService.failOrder(failOrderRequest)
//        return ResponseEntity.status(HttpStatus.OK).body(response)
//    }

//    @PostMapping
//    fun createOrder(@RequestBody createOrderRequest: CreateOrderRequest): ResponseEntity<OrderCreateResponse> {
//        val response = orderService.createOrder(createOrderRequest)
//        return ResponseEntity.status(HttpStatus.CREATED).body(response)
//    }



}
