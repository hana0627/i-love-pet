package hana.lovepet.productservice.api.product.service

import hana.lovepet.productservice.api.product.controller.dto.request.ProductRegisterRequest
import hana.lovepet.productservice.api.product.controller.dto.response.ProductInformationResponse
import hana.lovepet.productservice.api.product.controller.dto.response.ProductRegisterResponse
import hana.lovepet.productservice.infrastructure.kafka.`in`.dto.GetProductsEvent.OrderItemRequest
import hana.lovepet.productservice.infrastructure.kafka.`in`.dto.ProductStockDecreaseEvent
import hana.lovepet.productservice.infrastructure.kafka.`in`.dto.ProductStockRollbackEvent

interface ProductService {
    fun register(productRegisterRequest: ProductRegisterRequest): ProductRegisterResponse
    fun getProductInformation(productId: Long): ProductInformationResponse
    fun getAllProducts(): List<ProductInformationResponse>
    fun getProductsInformation(orderId: Long, products: List<OrderItemRequest>)
    fun decreaseStock(orderId: Long, productStockDecreaseRequests: List<ProductStockDecreaseEvent.Product>)

    fun rollbackStock(orderId: Long, rollbackProducts: List<ProductStockRollbackEvent.Product>)
//    fun getStock(productId: Long): Int
}
