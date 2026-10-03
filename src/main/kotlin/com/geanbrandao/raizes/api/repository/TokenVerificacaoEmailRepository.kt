package com.geanbrandao.raizes.api.repository

import com.geanbrandao.raizes.api.entity.TokenVerificacaoEmailEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/** Acesso aos codigos de verificação de e-mail. */
interface TokenVerificacaoEmailRepository : JpaRepository<TokenVerificacaoEmailEntity, UUID> {

    /**
     * Ultimo codigo emitido para o usuario que ainda não foi usado.
     *
     * Pega o mais recente porque um reenvio gera codigo novo, e o antigo deve
     * deixar de valer na pratica.
     */
    fun findFirstByUsuarioIdAndUsadoFalseOrderByCriadoEmDesc(
        usuarioId: UUID,
    ): TokenVerificacaoEmailEntity?

    /**
     * Ultimo codigo emitido para o usuario, usado ou não.
     *
     * Serve para quem confirma duas vezes o mesmo codigo: nesse caso o token ja
     * esta marcado como usado, e a busca de cima não acha nada.
     */
    fun findFirstByUsuarioIdOrderByCriadoEmDesc(
        usuarioId: UUID,
    ): TokenVerificacaoEmailEntity?
}
