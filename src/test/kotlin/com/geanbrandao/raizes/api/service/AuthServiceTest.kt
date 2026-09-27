package com.geanbrandao.raizes.api.service

import com.geanbrandao.raizes.api.dto.LoginRequest
import com.geanbrandao.raizes.api.exception.ApiException
import com.geanbrandao.raizes.api.exception.ErrorCodes
import com.geanbrandao.raizes.api.repository.RefreshTokenRepository
import com.geanbrandao.raizes.api.repository.UsuarioRepository
import com.geanbrandao.raizes.api.security.JwtService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.crypto.password.PasswordEncoder
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Testes de unidade do login, focados na proteção contra enumeração de e-mails.
 *
 * O teste de integração ja garante que a mensagem e a mesma nos dois casos. O que
 * falta garantir e que o **tempo** tambem seja, porque uma resposta que volta em 1 ms
 * quando o e-mail não existe e em 60 ms quando existe entrega exatamente a informação
 * que a mensagem tentava esconder.
 *
 * Medir tempo em teste daria resultado instavel, entao a verificação e indireta: o
 * BCrypt precisa ser chamado tambem quando o usuario não foi encontrado.
 */
class AuthServiceTest {

    private val usuarioRepository: UsuarioRepository = mock()
    private val refreshTokenRepository: RefreshTokenRepository = mock()
    private val jwtService: JwtService = mock()
    private val passwordEncoder: PasswordEncoder = mock()

    private val authService = AuthService(
        usuarioRepository = usuarioRepository,
        refreshTokenRepository = refreshTokenRepository,
        jwtService = jwtService,
        passwordEncoder = passwordEncoder,
        refreshExpiracaoMs = 604800000,
    )

    @Test
    fun `email inexistente ainda assim roda a comparacao de senha`() {
        whenever(usuarioRepository.findByEmail(any())).thenReturn(null)
        whenever(passwordEncoder.matches(any(), any())).thenReturn(false)

        assertFailsWith<ApiException> {
            authService.login(LoginRequest("naoexiste@exemplo.com", "Senha@123"))
        }

        // Este verify e o coração do teste: sem ele, um curto-circuito voltaria a
        // abrir o canal lateral de tempo sem nenhum teste reclamar.
        verify(passwordEncoder, times(1)).matches(any(), any())
    }

    @Test
    fun `email inexistente devolve credenciais invalidas e nao um erro de nao encontrado`() {
        whenever(usuarioRepository.findByEmail(any())).thenReturn(null)
        whenever(passwordEncoder.matches(any(), any())).thenReturn(false)

        val ex = assertFailsWith<ApiException> {
            authService.login(LoginRequest("naoexiste@exemplo.com", "Senha@123"))
        }

        assertEquals(ErrorCodes.CREDENCIAIS_INVALIDAS, ex.error)
        assertEquals("E-mail ou senha invalidos.", ex.message)
    }

    @Test
    fun `o hash descartavel nao deixa ninguem entrar`() {
        // Se o hash de fallback casasse com alguma senha, qualquer pessoa entraria
        // com um e-mail inexistente. O encoder real precisa recusar.
        whenever(usuarioRepository.findByEmail(any())).thenReturn(null)
        whenever(passwordEncoder.matches(any(), any())).thenReturn(true)

        val ex = assertFailsWith<ApiException> {
            authService.login(LoginRequest("naoexiste@exemplo.com", "qualquer-senha"))
        }
        assertEquals(ErrorCodes.CREDENCIAIS_INVALIDAS, ex.error)
    }
}
