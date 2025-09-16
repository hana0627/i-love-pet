package hana.lovepet.productservice.api.product.service.impl

import hana.lovepet.orderservice.common.exception.ApplicationException
import hana.lovepet.orderservice.common.exception.constant.ErrorCode
import hana.lovepet.productservice.api.product.controller.dto.request.ProductRegisterRequest
import hana.lovepet.productservice.api.product.controller.dto.response.ProductInformationResponse
import hana.lovepet.productservice.api.product.controller.dto.response.ProductRegisterResponse
import hana.lovepet.productservice.api.product.domain.Product
import hana.lovepet.productservice.api.product.repository.ProductCacheRepository
import hana.lovepet.productservice.api.product.repository.ProductRepository
import hana.lovepet.productservice.api.product.service.ProductService
import hana.lovepet.productservice.common.clock.TimeProvider
import hana.lovepet.productservice.infrastructure.kafka.`in`.dto.GetProductsEvent.OrderItemRequest
import hana.lovepet.productservice.infrastructure.kafka.`in`.dto.ProductStockDecreaseEvent
import hana.lovepet.productservice.infrastructure.kafka.`in`.dto.ProductStockRollbackEvent
import hana.lovepet.productservice.infrastructure.kafka.out.ProductEventPublisher
import hana.lovepet.productservice.infrastructure.kafka.out.dto.ProductStockDecreasedEvent
import hana.lovepet.productservice.infrastructure.kafka.out.dto.ProductsInformationResponseEvent
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
@Transactional(readOnly = true)
class ProductServiceImpl(
    private val productRepository: ProductRepository,
    private val timeProvider: TimeProvider,

    private val applicationEventPublisher: ApplicationEventPublisher,
    private val productEventPublisher: ProductEventPublisher,

    private val productCacheRepository: ProductCacheRepository,
) : ProductService {

    private val log = LoggerFactory.getLogger(ProductServiceImpl::class.java)


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

        if (missing.isNotEmpty()) {
            throw ApplicationException(ErrorCode.PRODUCT_NOT_FOUND, "다음 상품을 찾을 수 없습니다: $missing")
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

        // TODO (임시) 상품명에 "없는상품"이 있으면 예외처리
        foundProducts.forEach { it ->
            if (it.name.contains("없는상품")) {
                throw ApplicationException(ErrorCode.PRODUCT_NOT_FOUND, "다음 상품을 찾을 수 없습니다: ${it.id}")
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
    }

    @Transactional
    override fun decreaseStock(orderId: Long, productStockDecreaseRequests: List<ProductStockDecreaseEvent.Product>) {
        if (productCacheRepository.getDecreased(orderId)) {
            log.warn("이미 처리 중인 재고 차감 요청: orderId=$orderId")
            return
        }
        productCacheRepository.setDecreased(orderId)


        val ids = productStockDecreaseRequests.map { it.productId }

        val products = productRepository.findAllByIdWithLock(ids)


        // TODO 삭제
        // -- 예외상황 검증 추가 - start
        products.forEach { it ->
            if (it.name.contains("재고부족")) {
                throw ApplicationException(ErrorCode.NOT_ENOUGH_STOCK, ErrorCode.NOT_ENOUGH_STOCK.message+"productId: ${it.id}")
            }
        }
        // -- 예외상황 검증 추가 - end

        val productMap = products.associateBy { it.id }

        productStockDecreaseRequests.forEach {
            val product = productMap[it.productId]
                ?: throw ApplicationException(ErrorCode.PRODUCT_NOT_FOUND, "다음 상품을 찾을 수 없습니다: ${it.productId}")
            product.decreaseStock(it.quantity, timeProvider)
        }

        productRepository.saveAll(products)

        applicationEventPublisher.publishEvent(ProductStockDecreasedEvent(
            eventId = UUID.randomUUID().toString(),
            orderId = orderId,
            success = true,
            idempotencyKey = orderId.toString(),
        ))
    }

    @Transactional
    override fun rollbackStock(
        orderId: Long,
        rollbackProducts: List<ProductStockRollbackEvent.Product>,
    ) {

        if (productCacheRepository.getRollbacked(orderId)) {
            log.warn("이미 처리 중인 재고 롤백 요청: orderId=$orderId")
            return
        }
        productCacheRepository.setRollbacked(orderId)


        val ids = rollbackProducts.map { it.productId }

        val products = productRepository.findAllByIdWithLock(ids)
        val productMap = products.associateBy { it.id }

        rollbackProducts.forEach {
            val product = productMap[it.productId]
                ?: throw ApplicationException(ErrorCode.PRODUCT_NOT_FOUND, "다음 상품을 찾을 수 없습니다: ${it.productId}")
            product.increaseStock(it.quantity, timeProvider)
        }
        productRepository.saveAll(products)
    }

    private fun getProductOrException(productId: Long): Product {
        return productRepository.findById(productId)
            .orElseThrow { ApplicationException(ErrorCode.PRODUCT_NOT_FOUND, "다음 상품을 찾을 수 없습니다: $productId") }
    }


}