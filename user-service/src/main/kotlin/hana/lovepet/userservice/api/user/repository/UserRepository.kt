package hana.lovepet.userservice.api.user.repository

import hana.lovepet.userservice.api.user.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserRepository : JpaRepository<User, Long>{
//    fun existsById(id: Long): Boolean
}