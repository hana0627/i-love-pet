package hana.lovepet.orderservice.infrastructure.webClient.product

import hana.lovepet.orderservice.infrastructure.webClient.product.dto.ProductInformationResponse

interface ProductServiceClient {
    fun getProducts(productIds: List<Long>): List<ProductInformationResponse>
}
