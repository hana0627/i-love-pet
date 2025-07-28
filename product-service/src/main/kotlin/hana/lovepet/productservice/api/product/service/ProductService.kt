package hana.lovepet.productservice.api.product.service

import hana.lovepet.productservice.api.product.controller.dto.request.ProductRegisterRequest
import hana.lovepet.productservice.api.product.controller.dto.response.ProductInformationResponse
import hana.lovepet.productservice.api.product.controller.dto.response.ProductRegisterResponse

interface ProductService {
    fun register(productRegisterRequest: ProductRegisterRequest): ProductRegisterResponse
    fun getProductInformation(productId: Long): ProductInformationResponse
    fun getAllProducts(): List<ProductInformationResponse>
//    fun getStock(productId: Long): Int
}
