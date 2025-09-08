package hana.lovepet.productservice.api.product.service.impl

import hana.lovepet.productservice.api.product.controller.dto.request.ProductRegisterRequest
import hana.lovepet.productservice.api.product.controller.dto.request.ProductStockDecreaseRequest
import hana.lovepet.productservice.api.product.controller.dto.response.ProductInformationResponse
import hana.lovepet.productservice.api.product.controller.dto.response.ProductRegisterResponse
import hana.lovepet.productservice.api.product.controller.dto.response.ProductStockDecreaseResponse
import hana.lovepet.productservice.api.product.domain.Product
import hana.lovepet.productservice.api.product.repository.ProductRepository
import hana.lovepet.productservice.api.product.service.ProductService
import hana.lovepet.productservice.common.clock.TimeProvider
import hana.lovepet.productservice.infrastructure.kafka.`in`.dto.GetProductsEvent.OrderItemRequest
import hana.lovepet.productservice.infrastructure.kafka.out.ProductEventPublisher
import hana.lovepet.productservice.infrastructure.kafka.out.dto.ProductsInformationResponseEvent
import jakarta.persistence.EntityNotFoundException
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class ProductServiceImpl(
    private val productRepository: ProductRepository,
    private val timeProvider: TimeProvider,

    private val applicationEventPublisher: ApplicationEventPublisher,
    private  val productEventPublisher: ProductEventPublisher,
) : ProductService {

    @Transactional
    override fun register(productRegisterRequest: ProductRegisterRequest): ProductRegisterResponse {
        val product = Product(
            name = productRegisterRequest.productName,
            price = productRegisterRequest.price,
            stock = productRegisterRequest.stock,
            createdAt = timeProvider.now()
        )

        val savedProduct = productRepository.save(product)
        return ProductRegisterResponse(productId = savedProduct.id!!)
    }

    override fun getProductInformation(productId: Long): ProductInformationResponse {
        val foundProduct = getProductOrException(productId)
        return ProductInformationResponse(
            productId = foundProduct.id!!,
            productName = foundProduct.name,
            price = foundProduct.price,
            stock = foundProduct.stock,
        )
    }

    override fun getAllProducts(): List<ProductInformationResponse> {
        return productRepository.findAll().map {
            ProductInformationResponse(
                productId = it.id!!,
                productName = it.name,
                price = it.price,
                stock = it.stock,
            )
        }
    }

    override fun getProductsInformation(orderId: Long, products: List<OrderItemRequest>) {
        val ids = products.map { it -> it.productId }
        val productQuantityMap = products.associateBy { it.productId } // 수량 매핑용

        val foundProducts: List<Product> = productRepository.findAllById(ids)
        val foundIds = foundProducts.map { it.id }.toSet()
        val missing = ids.filterNot { it in foundIds }
        try {
            if (missing.isNotEmpty()) {
                throw EntityNotFoundException("다음 상품을 찾을 수 없습니다: $missing")
            }

            val result = foundProducts.map { entity ->
                val orderItem = productQuantityMap[entity.id!!]!!
                ProductsInformationResponseEvent.ProductInformationResponse(
                    productId = entity.id!!,
                    productName = entity.name,
                    price = entity.price,
                    stock = entity.stock,
                    quantity = orderItem.quantity
                )
            }

            // TODO 상품명에 "없는상품"이 있으면 예외처리
            foundProducts.forEach { it ->
                if(it.name.contains("없는상품")) {
                    throw EntityNotFoundException("다음 상품을 찾을 수 없습니다: $it.id")
                }
            }


            // 성공이벤트 발행
            productEventPublisher.publishProductsInformation(
                ProductsInformationResponseEvent(
                    eventId = UUID.randomUUID().toString(),
                    orderId = orderId,
                    success = true,
                    products = result,
                    )
            )


        } catch(e: Exception) {
            throw e
        }
    }

//    override fun getProductsInformation(ids: List<Long>): List<ProductInformationResponse> {
//        val entities: List<Product> = productRepository.findAllById(ids)
//        val foundIds = entities.map { it.id }.toSet()
//        val missing = ids.filterNot { it in foundIds }
//        if (missing.isNotEmpty()) {
//            throw EntityNotFoundException("다음 상품을 찾을 수 없습니다: $missing")
//        }
//
//        val temp = entities.map {
//            ProductInformationResponse(
//                productId = it.id!!,
//                productName = it.name,
//                price = it.price,
//                stock = it.stock,
//            )
//        }
//        return temp
//    }

    @Transactional
    override fun decreaseStock(productStockDecreaseRequests: List<ProductStockDecreaseRequest>): ProductStockDecreaseResponse {
        val ids = productStockDecreaseRequests.map {it.productId}

        val products = productRepository.findAllByIdWithLock(ids)


        // TODO 삭제
        // -- 예외상황 검증 추가 - start
        products.forEach { it ->
            if(it.name.contains("재고부족")) {
                throw IllegalStateException("재고가 부족합니다.")
            }
        }

        // -- 예외상황 검증 추가 - end

        val productMap = products.associateBy { it.id }

        productStockDecreaseRequests.forEach {
            val product = productMap[it.productId]
                ?: throw EntityNotFoundException("상품 ${it.productId} 없음")
            product.decreaseStock(it.quantity, timeProvider)
        }

        productRepository.saveAll(products)

        return ProductStockDecreaseResponse(true)
    }

//    override fun getStock(productId: Long): Int {
//        return getProductOrException(productId).stock
//    }

    private fun getProductOrException(productId: Long): Product {
        return productRepository.findById(productId)
            .orElseThrow { EntityNotFoundException("상품을 찾을 수 없습니다. [id = $productId]") }
    }
}