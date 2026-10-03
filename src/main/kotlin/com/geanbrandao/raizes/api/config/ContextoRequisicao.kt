package com.geanbrandao.raizes.api.config

import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

/**
 * Dados da requisição em curso, para quem precisa deles longe do controller.
 *
 * A trilha de auditoria precisa saber de onde veio a ação, mas carregar o
 * HttpServletRequest por toda a pilha de services sujaria as assinaturas e
 * amarraria regra de negocio a detalhe de web. Este componente le o que precisa do
 * contexto da thread e devolve nulo quando não ha requisição, o que acontece em job
 * agendado e em teste de unidade.
 */
@Component
class ContextoRequisicao {

    /**
     * IP de quem chamou.
     *
     * Considera o X-Forwarded-For porque em produção a aplicação fica atras de proxy,
     * e sem isso todo acesso ficaria registrado como vindo do balanceador. O primeiro
     * endereco da lista e o cliente original.
     */
    fun ip(): String? {
        val request = requisicaoAtual() ?: return null
        val encaminhado = request.getHeader("X-Forwarded-For")
        return if (!encaminhado.isNullOrBlank()) {
            encaminhado.split(",").first().trim().take(45)
        } else {
            request.remoteAddr?.take(45)
        }
    }

    /** Identificador da requisição, o mesmo que aparece no log e no corpo do erro. */
    fun requestId(): String? =
        requisicaoAtual()?.getAttribute(RequestIdFilter.ATTRIBUTE) as? String

    private fun requisicaoAtual(): HttpServletRequest? =
        (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request
}
