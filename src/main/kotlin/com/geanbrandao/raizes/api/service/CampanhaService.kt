package com.geanbrandao.raizes.api.service

import com.geanbrandao.raizes.api.domain.CanalPedido
import com.geanbrandao.raizes.api.domain.TipoCampanha
import com.geanbrandao.raizes.api.entity.CampanhaEntity
import com.geanbrandao.raizes.api.repository.CampanhaRepository
import com.geanbrandao.raizes.api.repository.ConsentimentoRepository
import com.geanbrandao.raizes.api.repository.UsuarioRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.Period
import java.util.UUID

/** Desconto escolhido para um pedido. */
data class DescontoAplicado(
    val valor: BigDecimal,
    val campanha: String?,
)

/**
 * Escolhe qual campanha aplicar a um pedido.
 *
 * Duas decisões de negocio moram aqui:
 *
 * 1. **Não acumula.** Se mais de uma campanha serve, vale a que dá o maior desconto,
 *    e so ela. Somar promoções abriria a porta para total negativo e para combinação
 *    que ninguem planejou.
 * 2. **Faixa etaria exige consentimento.** Campanha segmentada por idade só é
 *    considerada se o cliente tiver consentimento de PERFILAMENTO ativo. Sem isso,
 *    usar a idade dele para decidir preço seria tratar dado pessoal sem base legal.
 */
@Service
class CampanhaService(
    private val campanhaRepository: CampanhaRepository,
    private val usuarioRepository: UsuarioRepository,
    private val consentimentoRepository: ConsentimentoRepository,
) {
    private val logger = LoggerFactory.getLogger(CampanhaService::class.java)

    /**
     * Calcula o desconto para um pedido.
     *
     * @param unidadeId Unidade do pedido.
     * @param canalPedido Canal de origem.
     * @param clienteId Cliente, quando identificado.
     * @param subtotal Soma dos itens, antes do desconto.
     * @param data Data de referencia para a vigencia.
     * @return Desconto a aplicar e o nome da campanha, ou zero se nenhuma serve.
     */
    @Transactional(readOnly = true)
    fun calcularDesconto(
        unidadeId: UUID,
        canalPedido: CanalPedido,
        clienteId: UUID?,
        subtotal: BigDecimal,
        data: LocalDate = LocalDate.now(),
    ): DescontoAplicado {
        val candidatas = campanhaRepository.buscarCandidatas(unidadeId, canalPedido, data)
            .filter { podeAplicarAoCliente(it, clienteId) }

        val melhor = candidatas
            .map { it to valorDoDesconto(it, subtotal) }
            .filter { (_, valor) -> valor > BigDecimal.ZERO }
            .maxByOrNull { (_, valor) -> valor }
            ?: return DescontoAplicado(BigDecimal.ZERO, null)

        val (campanha, valor) = melhor
        // Desconto nunca pode passar do subtotal, senão o pedido viraria credito.
        val limitado = valor.min(subtotal).setScale(2, RoundingMode.HALF_UP)
        logger.debug("Campanha '{}' aplicada: desconto de {}", campanha.nome, limitado)
        return DescontoAplicado(limitado, campanha.nome)
    }

    /**
     * Diz se a campanha pode ser aplicada a este cliente.
     *
     * Campanha sem recorte de idade vale para todo mundo, inclusive pedido de balcão
     * sem cliente identificado. Com recorte de idade, exige cliente identificado, data
     * de nascimento informada e consentimento de perfilamento ativo.
     */
    private fun podeAplicarAoCliente(campanha: CampanhaEntity, clienteId: UUID?): Boolean {
        if (!campanha.exigeConsentimentoDePerfilamento) return true
        if (clienteId == null) return false

        val temConsentimento = consentimentoRepository
            .existsByUsuarioIdAndFinalidadeAndRevogadoEmIsNull(clienteId, FINALIDADE_PERFILAMENTO)
        if (!temConsentimento) return false

        val nascimento = usuarioRepository.findById(clienteId).orElse(null)?.dataNascimento
            ?: return false
        val idade = Period.between(nascimento, LocalDate.now()).years

        return (campanha.idadeMinima?.let { idade >= it } ?: true) &&
            (campanha.idadeMaxima?.let { idade <= it } ?: true)
    }

    /** Converte a campanha em reais de desconto sobre o subtotal. */
    private fun valorDoDesconto(campanha: CampanhaEntity, subtotal: BigDecimal): BigDecimal =
        when (campanha.tipo) {
            TipoCampanha.DESCONTO_PERCENTUAL ->
                subtotal.multiply(campanha.valor).divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
            TipoCampanha.DESCONTO_FIXO -> campanha.valor
            // Pontos em dobro não mexem no preço; valem na hora de creditar fidelidade.
            TipoCampanha.PONTOS_EXTRAS -> BigDecimal.ZERO
        }

    companion object {
        const val FINALIDADE_PERFILAMENTO = "PERFILAMENTO"
    }
}
