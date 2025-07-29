package hana.lovepet.orderservice.infrastructure.webClient.product.impl

import hana.lovepet.orderservice.infrastructure.webClient.product.ProductServiceClient
import hana.lovepet.orderservice.infrastructure.webClient.product.dto.ProductInformationResponse
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class ProductServiceClientImpl(
    builder: WebClient.Builder
) : ProductServiceClient {

    private val webClient = builder
        .baseUrl("http://product-service:8080")
        .build()

    override fun getProducts(productIds: List<Long>): List<ProductInformationResponse> {
        val ids = productIds.joinToString(",")

        return try {
            webClient.get()
                .uri { it.path("/api/products")
                    .queryParam("ids", ids)
                    .build() }
                .retrieve()
                .bodyToFlux(ProductInformationResponse::class.java)
                .collectList()
                .block()!!
                .also { responses ->
                    val foundIds = responses.map { it.productId }.toSet()
                    val missing = productIds.filterNot { it in foundIds }
                    if (missing.isNotEmpty()) {
                        throw RuntimeException("존재하지 않는 상품 ID: $missing")
                    }
                }
        } catch (e : Exception) {
            throw RuntimeException("error occurred while trying to get products [ids: $ids]")
        }
    }

}
