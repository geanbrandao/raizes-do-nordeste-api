package com.geanbrandao.raizes.api.service

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
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
 * Implementação de desenvolvimento: escreve no log em vez de enviar e-mail de verdade.
 *
 * E a unica implementação que existe hoje, e isso e decisão consciente de escopo.
 * Integrar com provedor real (Resend, SES, SMTP) não acrescentaria nada ao que este
 * trabalho se propõe a demonstrar, e traria chave de API, dominio verificado e custo.
 * O fluxo de verificação em si e real: codigo gerado, com validade, limite de
 * tentativas e confirmação obrigatoria.
 *
 * **Sobre escrever o codigo no log.** Log não e lugar de credencial: ele vai para
 * agregador, fica retido por meses e e lido por gente que não deveria ver codigo de
 * autenticação. Por isso o valor so aparece quando o modo de codigo fixo esta ligado,
 * que e o ambiente de desenvolvimento, onde o codigo e publico e documentado de
 * qualquer forma. Fora dali, o log registra que o codigo saiu, mas não qual e.
 *
 * Nesse caso o codigo fica inacessivel, e isso e proposital: escancara que falta um
 * provedor real em vez de deixar a aplicação funcionando pela metade em silencio. O
 * aviso no startup diz exatamente isso.
 *
 * Para produção, bastaria uma classe nova implementando [EnviadorDeEmail] e o
 * registro dela como bean no lugar desta.
 */
@Service
class EnviadorDeEmailLog(
    @Value("\${app.verificacao-email.codigo-fixo:}") private val codigoFixo: String,
) : EnviadorDeEmail {

    private val logger = LoggerFactory.getLogger(EnviadorDeEmailLog::class.java)

    /** Modo de desenvolvimento: codigo fixo e publico, entao pode aparecer no log. */
    private val ehAmbienteDeDesenvolvimento: Boolean get() = codigoFixo.isNotBlank()

    @PostConstruct
    fun avisarQueNaoHaProvedorReal() {
        if (!ehAmbienteDeDesenvolvimento) {
            logger.warn(
                "Nenhum provedor de e-mail real configurado. Os codigos de verificação " +
                    "não serão entregues a ninguem e não serão escritos no log. " +
                    "Implemente EnviadorDeEmail antes de usar isto fora de desenvolvimento.",
            )
        }
    }

    override fun enviarCodigoDeVerificacao(destinatario: String, codigo: String) {
        if (ehAmbienteDeDesenvolvimento) {
            logger.info(
                "[VERIFICACAO DE EMAIL] destinatario={} codigo={} " +
                    "(em producao isto seria um e-mail, nunca uma linha de log)",
                destinatario,
                codigo,
            )
        } else {
            logger.info(
                "[VERIFICACAO DE EMAIL] codigo emitido para {}; valor omitido do log " +
                    "por ser credencial",
                destinatario,
            )
        }
    }
}
