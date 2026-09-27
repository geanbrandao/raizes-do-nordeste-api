package com.geanbrandao.raizes.api.service

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Garante que o codigo de verificação não vaza no log fora do desenvolvimento.
 *
 * Log não e lugar de credencial. Em dev o valor pode aparecer, porque o codigo e fixo,
 * publico e documentado. Em qualquer outro ambiente ele precisa sumir do log, senão o
 * codigo de autenticação de todo mundo acaba no agregador de logs.
 */
class EnviadorDeEmailLogTest {

    private fun capturarLog(acao: (ListAppender<ILoggingEvent>) -> Unit) {
        val logger = LoggerFactory.getLogger(EnviadorDeEmailLog::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        try {
            acao(appender)
        } finally {
            logger.detachAppender(appender)
        }
    }

    @Test
    fun `em desenvolvimento o codigo aparece no log para facilitar o teste`() {
        capturarLog { appender ->
            EnviadorDeEmailLog(codigoFixo = "258369")
                .enviarCodigoDeVerificacao("joana@exemplo.com", "258369")

            val mensagens = appender.list.map { it.formattedMessage }
            assertTrue(mensagens.any { it.contains("258369") })
            assertTrue(mensagens.any { it.contains("joana@exemplo.com") })
        }
    }

    @Test
    fun `sem codigo fixo o valor nao pode aparecer no log`() {
        capturarLog { appender ->
            EnviadorDeEmailLog(codigoFixo = "")
                .enviarCodigoDeVerificacao("joana@exemplo.com", "914772")

            val mensagens = appender.list.map { it.formattedMessage }
            // O destinatario pode aparecer; o codigo, nunca.
            assertTrue(mensagens.any { it.contains("joana@exemplo.com") })
            assertFalse(
                mensagens.any { it.contains("914772") },
                "o codigo de verificação nunca pode ir para o log fora de desenvolvimento",
            )
        }
    }

    @Test
    fun `sem provedor real configurado sobe um aviso no startup`() {
        capturarLog { appender ->
            EnviadorDeEmailLog(codigoFixo = "").avisarQueNaoHaProvedorReal()
            assertTrue(
                appender.list.any { it.formattedMessage.contains("Nenhum provedor de e-mail real") },
            )
        }
    }

    @Test
    fun `em desenvolvimento nao sobe o aviso de provedor ausente`() {
        capturarLog { appender ->
            EnviadorDeEmailLog(codigoFixo = "258369").avisarQueNaoHaProvedorReal()
            assertTrue(appender.list.isEmpty())
        }
    }
}
