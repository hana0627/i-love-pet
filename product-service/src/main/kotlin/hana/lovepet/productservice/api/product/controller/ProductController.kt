package hana.lovepet.productservice.api.product.controller

import hana.lovepet.productservice.api.product.controller.dto.request.ProductRegisterRequest
import hana.lovepet.productservice.api.product.controller.dto.response.ProductInformationResponse
import hana.lovepet.productservice.api.product.controller.dto.response.ProductRegisterResponse
import hana.lovepet.productservice.api.product.service.ProductService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/products")
class ProductController(
    private val productService: ProductService
) {

    @PostMapping
    fun registerProduct(@RequestBody productRegisterRequest: ProductRegisterRequest): ResponseEntity<ProductRegisterResponse> {
        val response = productService.register(productRegisterRequest)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }


    @GetMapping("/{productId}")
    fun getProductInformation(@PathVariable("productId") productId: Long): ResponseEntity<ProductInformationResponse> {
        val response = productService.getProductInformation(productId)
        return ResponseEntity.ok(response)
    }

    @GetMapping
    fun getProducts(): ResponseEntity<List<ProductInformationResponse>> {
        val response = productService.getAllProducts();
        return ResponseEntity.ok(response)
    }

    @GetMapping(params = ["ids"])
    fun getProducts(@RequestParam("ids") ids: List<Long>): ResponseEntity<List<ProductInformationResponse>> {
        val response = productService.getProductsInformation(ids)
        return ResponseEntity.ok(response)
    }

//    @GetMapping("/{productId}/stock")
//    fun getStock(@PathVariable("productId") productId: Long): ResponseEntity<Int> {
//        val response = productService.getStock(productId)
//        return ResponseEntity.ok(response)
//    }

}
