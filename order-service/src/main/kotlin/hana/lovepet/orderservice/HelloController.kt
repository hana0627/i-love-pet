package hana.lovepet.orderservice

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HelloController {

    @GetMapping("/")
    fun index(): String {
        return "order-service is health"
    }

    @GetMapping("/hello")
    fun hello(): String {
        return "order-service hello"
    }
}