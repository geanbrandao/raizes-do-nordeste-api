package com.geanbrandao.raizes.api.repository

import com.geanbrandao.raizes.api.entity.LogAuditoriaEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

/** Acesso a trilha de auditoria. So leitura e inserção, nunca update ou delete. */
interface LogAuditoriaRepository : JpaRepository<LogAuditoriaEntity, UUID> {

    /**
     * Consulta da trilha com filtros opcionais, usada pelo admin.
     *
     * @param usuarioId Filtra por quem fez a ação, ou null para todos.
     * @param entidade Filtra pelo tipo de registro afetado, ou null para todos.
     * @param pageable Pagina e tamanho da pagina.
     * @return Pagina de registros, do mais novo para o mais antigo.
     */
    @Query(
        """
        SELECT l FROM LogAuditoriaEntity l
        WHERE (:usuarioId IS NULL OR l.usuarioId = :usuarioId)
          AND (:entidade IS NULL OR l.entidade = :entidade)
        ORDER BY l.criadoEm DESC
        """
    )
    fun buscarComFiltros(
        @Param("usuarioId") usuarioId: UUID?,
        @Param("entidade") entidade: String?,
        pageable: Pageable,
    ): Page<LogAuditoriaEntity>

    fun findAllByEntidadeAndEntidadeId(entidade: String, entidadeId: UUID): List<LogAuditoriaEntity>
}
