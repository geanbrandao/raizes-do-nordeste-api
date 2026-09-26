package com.geanbrandao.raizes.api.repository

import com.geanbrandao.raizes.api.entity.RefreshTokenEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

/** Acesso aos refresh tokens. */
interface RefreshTokenRepository : JpaRepository<RefreshTokenEntity, UUID> {

    fun findByToken(token: String): RefreshTokenEntity?

    /**
     * Derruba todas as sessões do usuario de uma vez.
     *
     * flushAutomatically e clearAutomatically não são enfeite: um update via JPQL
     * passa por fora do contexto de persistencia. Sem limpar o contexto, uma leitura
     * na mesma transação devolveria a entidade em cache, ainda marcada como não
     * revogada, e o logout pareceria não ter funcionado.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE RefreshTokenEntity r SET r.revogado = true WHERE r.usuarioId = :usuarioId")
    fun revogarTodosDoUsuario(@Param("usuarioId") usuarioId: UUID): Int
}
