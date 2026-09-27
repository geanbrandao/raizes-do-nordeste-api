package com.geanbrandao.raizes.api.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Porta de saida para envio de e-mail.
 *
 * O resto do sistema depende desta interface, não de um provedor especifico. Trocar
 * o envio por Resend, SES ou SMTP amanhã e escrever outra implementação e registrar
 * como bean, sem tocar em regra de negocio.
 */
interface EnviadorDeEmail {

    /**
     * Entrega o codigo de verificação ao dono do endereco.
     *
     * @param destinatario E-mail de destino.
     * @param codigo Codigo de 6 digitos.
     */
    fun enviarCodigoDeVerificacao(destinatario: String, codigo: String)
}

/**
 * Implementação que escreve o codigo no log em vez de enviar e-mail de verdade.
 *
 * E a unica implementação que existe hoje, e isso e uma decisão consciente de
 * escopo: integrar com provedor real (Resend, SES, SMTP) não acrescentaria nada ao
 * que este trabalho se propõe a demonstrar, e traria chave de API, dominio
 * verificado e custo. O fluxo de verificação em si e real: codigo gerado, com
 * validade, limite de tentativas e confirmação obrigatoria.
 *
 * Para produção, bastaria uma classe nova implementando [EnviadorDeEmail] e o
 * registro dela como bean no lugar desta.
 */
@Service
class EnviadorDeEmailLog : EnviadorDeEmail {

    private val logger = LoggerFactory.getLogger(EnviadorDeEmailLog::class.java)

    override fun enviarCodigoDeVerificacao(destinatario: String, codigo: String) {
        logger.info(
            "[VERIFICACAO DE EMAIL] destinatario={} codigo={} " +
                "(em producao isto seria um e-mail, nunca uma linha de log)",
            destinatario,
            codigo,
        )
    }
}
