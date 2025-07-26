package hana.lovepet.userservice.api.user.domain

import hana.lovepet.userservice.common.clock.TimeProvider
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "users")
class User(
    @Column(nullable = false, length = 30)
    val name: String,
    @Column(nullable = false, length = 50)
    val email: String,
    @Column(nullable = true, name = "phone_number", length = 20)
    var phoneNumber: String,

    @Column(nullable = false, name = "created_at")
    val createdAt: LocalDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null


    companion object {
        fun fixture(
            name: String = "박하나",
            email: String = "hanana@lovepet.com",
            phoneNumber: String = "01036066270",
            timeProvider: TimeProvider
        ): User {
            return User(
                name = name,
                email = email,
                phoneNumber = phoneNumber,
                createdAt = timeProvider.now()
            )
        }
    }
}
