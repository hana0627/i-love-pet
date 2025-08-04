package hana.lovepet.orderservice.infrastructure.webClient.product.impl

import hana.lovepet.orderservice.infrastructure.webClient.product.ProductServiceClient
import hana.lovepet.orderservice.infrastructure.webClient.product.dto.request.ProductStockDecreaseRequest
import hana.lovepet.orderservice.infrastructure.webClient.product.dto.response.ProductInformationResponse
import hana.lovepet.orderservice.infrastructure.webClient.product.dto.response.ProductStockDecreaseResponse
import org.springframework.http.MediaType.APPLICATION_JSON
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


    override fun decreaseStock(requests: List<ProductStockDecreaseRequest>): ProductStockDecreaseResponse {

        return try {
            webClient.patch()
                .uri { it.path("/api/products/decrease-stock")
                    .build() }
                .contentType(APPLICATION_JSON)
                .bodyValue(requests)
                .retrieve()
                .bodyToMono(ProductStockDecreaseResponse::class.java)
                .block() ?: throw IllegalStateException("재고 차감 응답이 null 입니다.")
        } catch (e : Exception) {
            throw RuntimeException("error occurred while trying to decrease product stocks : ${e.message}")
        }
    }
}
