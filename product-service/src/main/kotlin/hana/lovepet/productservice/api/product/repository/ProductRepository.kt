package hana.lovepet.productservice.api.product.repository

import hana.lovepet.productservice.api.product.domain.Product
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface ProductRepository: JpaRepository<Product, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id IN :ids")
    fun findAllByIdWithLock(ids : List<Long>): List<Product>
}