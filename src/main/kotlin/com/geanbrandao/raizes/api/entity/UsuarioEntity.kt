package com.geanbrandao.raizes.api.entity

import com.geanbrandao.raizes.api.domain.Perfil
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/**
 * Usuario do sistema, seja cliente ou funcionario.
 *
 * A senha nunca e guardada em texto: o campo e o hash BCrypt e ele nunca sai em
 * nenhuma resposta da API.
 *
 * [unidadeId] so vale para operador (gerente, atendente, cozinha) e e o que amarra
 * a pessoa na loja dela. Cliente e admin não tem unidade. O banco cobra isso numa
 * constraint, então não da para gravar um atendente solto sem loja.
 */
@Entity
@Table(name = "usuarios")
class UsuarioEntity(

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "nome", nullable = false, length = 120)
    var nome: String,

    @Column(name = "email", nullable = false, length = 254)
    var email: String,

    /** Hash BCrypt. Nunca expor em response. */
    @Column(name = "senha_hash", nullable = false, length = 100)
    var senhaHash: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "perfil", nullable = false, length = 20)
    var perfil: Perfil,

    /** Loja onde a pessoa trabalha. Nulo para cliente e admin. */
    @Column(name = "unidade_id")
    var unidadeId: UUID? = null,

    /**
     * Dado pessoal coletado so para segmentar campanha de fidelidade.
     * Opcional de proposito: sem consentimento, não precisa existir (minimização da LGPD).
     */
    @Column(name = "data_nascimento")
    var dataNascimento: LocalDate? = null,

    /**
     * Se o dono do endereco ja confirmou o codigo enviado no cadastro.
     *
     * Conta não verificada existe no banco mas não loga. E isso que permite o
     * cadastro responder a mesma coisa para e-mail novo e ja existente: criar conta
     * com o endereco de outra pessoa não da acesso a nada.
     */
    @Column(name = "email_verificado", nullable = false)
    var emailVerificado: Boolean = false,

    @Column(name = "ativo", nullable = false)
    var ativo: Boolean = true,

    @Column(name = "criado_em", nullable = false, updatable = false)
    val criadoEm: LocalDateTime = LocalDateTime.now(),

    @Column(name = "atualizado_em", nullable = false)
    var atualizadoEm: LocalDateTime = LocalDateTime.now(),
)
