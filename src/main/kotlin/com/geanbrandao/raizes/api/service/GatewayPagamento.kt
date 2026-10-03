package com.geanbrandao.raizes.api.service

import com.geanbrandao.raizes.api.domain.MetodoPagamento
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.UUID

/** O que o gateway respondeu sobre uma cobrança. */
data class RespostaGateway(
    val resultado: ResultadoGateway,
    val idTransacaoExterna: String? = null,
    val mensagem: String,
    /** Resposta crua, guardada para conferencia depois. */
    val payload: String,
)

/** Desfecho possivel de uma chamada ao gateway. */
enum class ResultadoGateway {
    APROVADO,
    RECUSADO,

    /**
     * O gateway não respondeu a tempo.
     *
     * Não e o mesmo que recusa: a cobrança pode ter sido processada do outro lado. Por
     * isso o pagamento fica pendente esperando o callback, em vez de ser dado como
     * perdido.
     */
    SEM_RESPOSTA,
}

/** Dados que a rede envia ao gateway para pedir uma cobrança. */
data class RequisicaoGateway(
    val pedidoId: UUID,
    val valor: BigDecimal,
    val metodo: MetodoPagamento,
    val tokenPagamento: String?,
)

/**
 * Porta de saida para o serviço externo de pagamento.
 *
 * A rede não processa pagamento: ela solicita, recebe a confirmação ou a negativa,
 * registra o resultado e atualiza o status do pedido. Depender desta interface, e não
 * de um provedor, e o que permite trocar o gateway sem tocar em regra de negocio.
 */
interface GatewayPagamento {

    /**
     * Pede a cobrança ao gateway.
     *
     * @param requisicao Pedido, valor, metodo e token.
     * @return O que o gateway respondeu.
     */
    fun solicitar(requisicao: RequisicaoGateway): RespostaGateway
}

/**
 * Gateway simulado. E a unica implementação que existe.
 *
 * Integrar com um provedor real está fora do escopo deste trabalho, e o proprio
 * estudo de caso pede a simulação. O que importa demonstrar e o **fluxo**: a rede
 * solicita, trata aprovação e recusa, aguenta o gateway não responder e atualiza o
 * pedido conforme o desfecho. Isso tudo e real aqui.
 *
 * **O resultado e deterministico, nunca aleatorio.** Quem for testar precisa
 * conseguir reproduzir os tres desfechos quando quiser, e teste que sorteia resultado
 * falha sozinho de vez em quando. O token enviado decide:
 *
 * | Token começando com | Desfecho |
 * |---|---|
 * | `tok_recusa` | RECUSADO |
 * | `tok_timeout` | SEM_RESPOSTA (gateway não respondeu) |
 * | qualquer outro, ou ausente | APROVADO |
 *
 * Repare que a API **não recebe dado de cartão**. O cliente manda um token opaco, que
 * num cenario real viria do SDK do proprio gateway. Numero de cartão nunca entra
 * nesta aplicação, o que mantem o dado sensivel fora do nosso banco e do nosso log.
 */
@Service
class GatewayPagamentoMock(
    @Value("\${app.pagamento.mock-timeout-ms:2000}") private val timeoutMs: Long,
) : GatewayPagamento {

    private val logger = LoggerFactory.getLogger(GatewayPagamentoMock::class.java)

    override fun solicitar(requisicao: RequisicaoGateway): RespostaGateway {
        val token = requisicao.tokenPagamento.orEmpty()
        logger.info(
            "[GATEWAY MOCK] cobrança solicitada: pedido={} valor={} metodo={}",
            requisicao.pedidoId, requisicao.valor, requisicao.metodo,
        )

        return when {
            token.startsWith(PREFIXO_RECUSA) -> RespostaGateway(
                resultado = ResultadoGateway.RECUSADO,
                idTransacaoExterna = "mock_${UUID.randomUUID()}",
                mensagem = "Pagamento recusado pela operadora.",
                payload = """{"status":"refused","reason":"insufficient_funds","gateway":"mock"}""",
            )

            token.startsWith(PREFIXO_TIMEOUT) -> RespostaGateway(
                resultado = ResultadoGateway.SEM_RESPOSTA,
                idTransacaoExterna = null,
                mensagem = "O serviço de pagamento não respondeu a tempo (${timeoutMs}ms).",
                payload = """{"status":"timeout","gateway":"mock"}""",
            )

            else -> RespostaGateway(
                resultado = ResultadoGateway.APROVADO,
                idTransacaoExterna = "mock_${UUID.randomUUID()}",
                mensagem = "Pagamento aprovado.",
                payload = """{"status":"approved","gateway":"mock"}""",
            )
        }
    }

    companion object {
        const val PREFIXO_RECUSA = "tok_recusa"
        const val PREFIXO_TIMEOUT = "tok_timeout"
    }
}
