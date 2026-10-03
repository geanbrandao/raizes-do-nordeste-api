package com.geanbrandao.raizes.api.service

import com.geanbrandao.raizes.api.entity.TokenVerificacaoEmailEntity
import com.geanbrandao.raizes.api.entity.UsuarioEntity
import com.geanbrandao.raizes.api.exception.ApiException
import com.geanbrandao.raizes.api.exception.ErrorCodes
import com.geanbrandao.raizes.api.repository.TokenVerificacaoEmailRepository
import com.geanbrandao.raizes.api.repository.UsuarioRepository
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.LocalDateTime

/**
 * Emissão e conferencia dos codigos de verificação de e-mail.
 *
 * Sobre o codigo fixo em desenvolvimento: quando `app.verificacao-email.codigo-fixo`
 * esta preenchido, todo codigo emitido e aquele valor. Existe para o ambiente de
 * avaliação ser reproduzivel, ja que quem for testar a API não tem caixa de entrada
 * para consultar. Em produção a chave fica vazia e o codigo passa a ser sorteado
 * com [SecureRandom]. O mecanismo e o mesmo nos dois casos, so a origem do numero
 * muda, e um aviso sobe no log quando o modo fixo esta ligado.
 */
@Service
class VerificacaoEmailService(
    private val usuarioRepository: UsuarioRepository,
    private val tokenRepository: TokenVerificacaoEmailRepository,
    private val enviadorDeEmail: EnviadorDeEmail,
    @Value("\${app.verificacao-email.codigo-fixo:}") private val codigoFixo: String,
    @Value("\${app.verificacao-email.validade-horas:24}") private val validadeHoras: Long,
    @Value("\${app.verificacao-email.max-tentativas:5}") private val maxTentativas: Int,
) {
    private val logger = LoggerFactory.getLogger(VerificacaoEmailService::class.java)
    private val sorteio = SecureRandom()

    /**
     * Avisa no boot quando o codigo de verificação esta fixo.
     *
     * Codigo previsivel e proposital em desenvolvimento, para dar para testar o fluxo
     * sem caixa de entrada. Em producão seria furo grave, então o aviso fica no log de
     * inicialização, onde não passa batido.
     */
    @PostConstruct
    fun avisarSobreCodigoFixo() {
        if (codigoFixo.isNotBlank()) {
            logger.warn(
                "Codigo de verificação FIXO ligado ({}). Aceitavel em dev; " +
                    "em producao a chave app.verificacao-email.codigo-fixo deve ficar vazia.",
                codigoFixo,
            )
        }
    }

    /**
     * Emite um codigo novo e manda para o dono do endereco.
     *
     * @param usuario Conta que precisa confirmar o e-mail.
     */
    @Transactional
    fun emitirCodigo(usuario: UsuarioEntity) {
        val codigo = gerarCodigo()
        tokenRepository.save(
            TokenVerificacaoEmailEntity(
                usuarioId = usuario.id,
                codigo = codigo,
                expiraEm = LocalDateTime.now().plusHours(validadeHoras),
            ),
        )
        enviadorDeEmail.enviarCodigoDeVerificacao(usuario.email, codigo)
    }

    /**
     * Confere o codigo e libera a conta.
     *
     * Erro de codigo e e-mail inexistente devolvem exatamente a mesma resposta, pelo
     * mesmo motivo do login: senão este endpoint viraria a forma mais facil de
     * descobrir quem tem conta na rede.
     *
     * @param email E-mail da conta.
     * @param codigo Codigo de 6 digitos recebido.
     * @throws ApiException 400 se o codigo não conferir, tiver expirado ou estourado as tentativas.
     */
    @Transactional
    fun confirmar(email: String, codigo: String) {
        val usuario = usuarioRepository.findByEmail(email.trim().lowercase())
            ?: throw codigoInvalido()

        if (usuario.emailVerificado) {
            // Mandar de novo o mesmo codigo certo não e erro: o resultado desejado ja vale.
            // Mas codigo errado tem que responder igual ao de uma conta não verificada. Se
            // a conta ja verificada devolvesse 204 para qualquer codigo, bastava chutar um
            // codigo qualquer para saber quais e-mails existem e estão ativos — a mesma
            // enumeração que o 202 generico do cadastro evita.
            val ultimo = tokenRepository.findFirstByUsuarioIdOrderByCriadoEmDesc(usuario.id)
            if (ultimo != null && ultimo.codigo == codigo.trim()) return
            throw codigoInvalido()
        }

        val token = tokenRepository.findFirstByUsuarioIdAndUsadoFalseOrderByCriadoEmDesc(usuario.id)
            ?: throw codigoInvalido()

        if (!token.estaUtilizavel(maxTentativas)) throw codigoInvalido()

        if (token.codigo != codigo.trim()) {
            token.tentativasFalhas += 1
            tokenRepository.save(token)
            logger.warn("Codigo de verificação incorreto ({} tentativa(s))", token.tentativasFalhas)
            throw codigoInvalido()
        }

        token.usado = true
        tokenRepository.save(token)

        usuario.emailVerificado = true
        usuario.atualizadoEm = LocalDateTime.now()
        usuarioRepository.save(usuario)

        logger.info("E-mail confirmado para o usuario {}", usuario.id)
    }

    /**
     * Reemite o codigo, se a conta existir e ainda não estiver verificada.
     *
     * Quem chama nunca descobre se alguma dessas condições foi atendida: a resposta
     * do controller e sempre a mesma.
     *
     * @param email E-mail informado.
     */
    @Transactional
    fun reenviar(email: String) {
        val usuario = usuarioRepository.findByEmail(email.trim().lowercase()) ?: return
        if (usuario.emailVerificado) return
        emitirCodigo(usuario)
    }

    /** Sorteia 6 digitos, ou devolve o codigo fixo quando ele esta configurado. */
    private fun gerarCodigo(): String =
        codigoFixo.ifBlank { sorteio.nextInt(1_000_000).toString().padStart(6, '0') }

    private fun codigoInvalido() = ApiException(
        error = ErrorCodes.CODIGO_VERIFICACAO_INVALIDO,
        message = "Codigo de verificação invalido ou expirado.",
        status = HttpStatus.BAD_REQUEST,
    )
}
