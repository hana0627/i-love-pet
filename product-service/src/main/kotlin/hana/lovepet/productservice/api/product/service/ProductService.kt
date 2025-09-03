package hana.lovepet.productservice.api.product.service

import hana.lovepet.productservice.api.product.controller.dto.request.ProductRegisterRequest
import hana.lovepet.productservice.api.product.controller.dto.request.ProductStockDecreaseRequest
import hana.lovepet.productservice.api.product.controller.dto.response.ProductInformationResponse
import hana.lovepet.productservice.api.product.controller.dto.response.ProductRegisterResponse
import hana.lovepet.productservice.api.product.controller.dto.response.ProductStockDecreaseResponse
import hana.lovepet.productservice.infrastructure.kafka.`in`.dto.GetProductsEvent.OrderItemRequest

interface ProductService {
    fun register(productRegisterRequest: ProductRegisterRequest): ProductRegisterResponse
    fun getProductInformation(productId: Long): ProductInformationResponse
    fun getAllProducts(): List<ProductInformationResponse>
//    fun getProductsInformation(orderId: Long, products: List<OrderItemRequest>): List<ProductInformationResponse>
    fun getProductsInformation(orderId: Long, products: List<OrderItemRequest>)
    fun decreaseStock(productStockDecreaseRequests: List<ProductStockDecreaseRequest>): ProductStockDecreaseResponse
//    fun getStock(productId: Long): Int
}
