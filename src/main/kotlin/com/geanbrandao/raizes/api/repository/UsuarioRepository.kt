package com.geanbrandao.raizes.api.repository

import com.geanbrandao.raizes.api.domain.Perfil
import com.geanbrandao.raizes.api.entity.UsuarioEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/** Acesso aos usuarios (clientes e operadores). */
interface UsuarioRepository : JpaRepository<UsuarioEntity, UUID> {

    /** Usado no login. */
    fun findByEmail(email: String): UsuarioEntity?

    /** Checagem de e-mail duplicado no cadastro. */
    fun existsByEmail(email: String): Boolean

    fun findAllByUnidadeId(unidadeId: UUID): List<UsuarioEntity>

    fun countByPerfil(perfil: Perfil): Long
}
