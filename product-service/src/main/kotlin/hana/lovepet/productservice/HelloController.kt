package hana.lovepet.productservice

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HelloController {

    @GetMapping("/")
    fun index(): String {
        return "product-service is health"
    }

    @GetMapping("/hello")
    fun hello(): String {
        return "product-service hello"
    }
}
