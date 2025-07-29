package hana.lovepet.orderservice.infrastructure.webClient.product

import hana.lovepet.orderservice.infrastructure.webClient.product.dto.ProductInfoResponse

interface ProductServiceClient {
    fun getProducts(productIds: List<Long>): List<ProductInfoResponse>
}
