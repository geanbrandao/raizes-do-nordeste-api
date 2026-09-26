package com.geanbrandao.raizes.api.repository

import com.geanbrandao.raizes.api.entity.ContaFidelidadeEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/** Acesso as contas do programa de fidelidade. */
interface ContaFidelidadeRepository : JpaRepository<ContaFidelidadeEntity, UUID> {

    fun findByClienteId(clienteId: UUID): ContaFidelidadeEntity?
}
