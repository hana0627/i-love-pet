package hana.lovepet.userservice

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController


@RestController
class HelloController {

    @GetMapping("/")
    fun index(): String {
        return "user-service is health"
    }

    @GetMapping("/hello")
    fun hello(): String {
        return "user-service hello"
    }
}