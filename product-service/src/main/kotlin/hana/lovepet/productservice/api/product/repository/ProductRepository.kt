package hana.lovepet.productservice.api.product.repository

import hana.lovepet.productservice.api.product.domain.Product
import org.springframework.data.jpa.repository.JpaRepository

interface ProductRepository: JpaRepository<Product, Long> {
}