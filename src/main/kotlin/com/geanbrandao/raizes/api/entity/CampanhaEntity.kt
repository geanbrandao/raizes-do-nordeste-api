package com.geanbrandao.raizes.api.entity

import com.geanbrandao.raizes.api.domain.CanalPedido
import com.geanbrandao.raizes.api.domain.TipoCampanha
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/**
 * Campanha ou promoção da rede.
 *
 * A segmentação funciona por exclusão: campo nulo quer dizer "sem restrição". Uma
 * campanha com [unidadeId] nulo vale para a rede toda; com [canalPedido] nulo vale
 * para qualquer canal; sem faixa de idade vale para qualquer cliente.
 *
 * A faixa etaria merece atenção: filtrar cliente por idade e perfilamento, e so
 * pode rodar para quem deu consentimento para isso. Quem checa essa parte e o
 * service, não a entidade.
 */
@Entity
@Table(name = "campanhas")
class CampanhaEntity(

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "nome", nullable = false, length = 120)
    var nome: String,

    @Column(name = "descricao", length = 500)
    var descricao: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 30)
    var tipo: TipoCampanha,

    /** Como ler esse numero depende do [tipo]: percentual, reais ou multiplicador. */
    @Column(name = "valor", nullable = false, precision = 10, scale = 2)
    var valor: BigDecimal,

    /** Nulo quer dizer campanha da rede inteira. */
    @Column(name = "unidade_id")
    var unidadeId: UUID? = null,

    /** Nulo quer dizer que vale em qualquer canal. */
    @Enumerated(EnumType.STRING)
    @Column(name = "canal_pedido", length = 20)
    var canalPedido: CanalPedido? = null,

    /** Faixa etaria minima. So aplicar com consentimento de perfilamento. */
    @Column(name = "idade_minima")
    var idadeMinima: Int? = null,

    /** Faixa etaria maxima. So aplicar com consentimento de perfilamento. */
    @Column(name = "idade_maxima")
    var idadeMaxima: Int? = null,

    @Column(name = "vigencia_inicio", nullable = false)
    var vigenciaInicio: LocalDate,

    @Column(name = "vigencia_fim", nullable = false)
    var vigenciaFim: LocalDate,

    @Column(name = "ativa", nullable = false)
    var ativa: Boolean = true,

    @Column(name = "criado_em", nullable = false, updatable = false)
    val criadoEm: LocalDateTime = LocalDateTime.now(),

    @Column(name = "atualizado_em", nullable = false)
    var atualizadoEm: LocalDateTime = LocalDateTime.now(),
) {
    /**
     * Diz se a campanha esta valendo na data informada.
     *
     * @param data Data a conferir, normalmente hoje.
     * @return true se esta ativa e dentro da vigencia.
     */
    fun estaVigente(data: LocalDate = LocalDate.now()): Boolean =
        ativa && !data.isBefore(vigenciaInicio) && !data.isAfter(vigenciaFim)

    /** Campanha que segmenta por idade e so pode rodar com consentimento. */
    val exigeConsentimentoDePerfilamento: Boolean
        get() = idadeMinima != null || idadeMaxima != null
}
