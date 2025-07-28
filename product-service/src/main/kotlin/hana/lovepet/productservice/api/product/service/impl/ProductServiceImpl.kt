package hana.lovepet.productservice.api.product.service.impl

import hana.lovepet.productservice.api.product.controller.dto.request.ProductRegisterRequest
import hana.lovepet.productservice.api.product.controller.dto.response.ProductInformationResponse
import hana.lovepet.productservice.api.product.controller.dto.response.ProductRegisterResponse
import hana.lovepet.productservice.api.product.domain.Product
import hana.lovepet.productservice.api.product.repository.ProductRepository
import hana.lovepet.productservice.api.product.service.ProductService
import hana.lovepet.productservice.common.clock.TimeProvider
import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ProductServiceImpl(
    private val productRepository: ProductRepository,
    private val timeProvider: TimeProvider
) : ProductService {

    @Transactional
    override fun register(productRegisterRequest: ProductRegisterRequest): ProductRegisterResponse {
        val product = Product(
            name = productRegisterRequest.name,
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
            name = foundProduct.name,
            price = foundProduct.price,
            stock = foundProduct.stock,
        )
    }

    override fun getAllProducts(): List<ProductInformationResponse> {
        return productRepository.findAll().map {
            ProductInformationResponse(
                name = it.name,
                price = it.price,
                stock = it.stock,
            )
        }
    }

//    override fun getStock(productId: Long): Int {
//        return getProductOrException(productId).stock
//    }

    private fun getProductOrException(productId: Long): Product {
        return productRepository.findById(productId)
            .orElseThrow { EntityNotFoundException("상품을 찾을 수 없습니다. [id = $productId]") }
    }
}