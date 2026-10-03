package com.geanbrandao.raizes.api.security

import com.geanbrandao.raizes.api.exception.ErrorCodes
import com.geanbrandao.raizes.api.exception.NaoAutenticadoException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.MessageDigest

/**
 * Autentica o callback do gateway de pagamento.
 *
 * A rota do callback e publica porque quem chama e o gateway, que não tem conta nesta
 * API. No lugar do token JWT ela usa um segredo combinado, enviado no header
 * `X-Gateway-Assinatura`.
 *
 * Mora em `security/` junto com o filtro de JWT por ser a mesma coisa que ele faz:
 * decidir se a requisição pode entrar. O controller so chama e segue.
 */
@Component
class AssinaturaCallbackGateway(
    @Value("\${app.pagamento.segredo-callback:}") private val segredo: String,
) {

    /**
     * Deixa passar so quem apresentou o segredo certo.
     *
     * A comparação usa [MessageDigest.isEqual], que gasta o mesmo tempo acertando ou
     * errando. Comparar com `==` sai mais cedo no primeiro caractere diferente, e quem
     * mede esse tempo descobre o segredo um caractere por vez.
     *
     * Ambiente sem segredo configurado recusa tudo, em vez de liberar tudo: assinatura
     * vazia conferindo com segredo vazio deixaria a rota aberta para qualquer um.
     *
     * @param assinatura Conteudo do header `X-Gateway-Assinatura`, ou nulo se não veio.
     * @throws NaoAutenticadoException 401 se o segredo não conferir ou não estiver configurado.
     */
    fun exigirValida(assinatura: String?) {
        if (segredo.isBlank()) {
            throw NaoAutenticadoException(
                error = ErrorCodes.NAO_AUTENTICADO,
                message = "Callback de pagamento não esta configurado neste ambiente.",
            )
        }
        val confere = MessageDigest.isEqual(
            assinatura.orEmpty().toByteArray(Charsets.UTF_8),
            segredo.toByteArray(Charsets.UTF_8),
        )
        if (!confere) {
            throw NaoAutenticadoException(
                error = ErrorCodes.NAO_AUTENTICADO,
                message = "Assinatura do gateway invalida.",
            )
        }
    }
}
