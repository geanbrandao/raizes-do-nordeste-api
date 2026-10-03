package com.geanbrandao.raizes.api.security

import com.geanbrandao.raizes.api.domain.Perfil
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

/**
 * Configuração de segurança da API.
 *
 * Duas escolhas que valem explicar:
 *
 * 1. **Tudo fechado por padrão.** A ultima regra e anyRequest().authenticated(), o
 *    que significa que endpoint novo ja nasce protegido. Quem quiser deixar uma
 *    rota publica precisa escrever isso aqui, de proposito. O contrario, liberar
 *    por padrão e lembrar de fechar depois, e como vaza rota sem querer.
 *
 * 2. **Sem sessão.** A API e stateless e a identidade vem do JWT a cada requisição.
 *    Isso permite rodar varias instancias atras de um balanceador sem compartilhar
 *    sessão, que e o que sustenta o requisito de aguentar horario de pico.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig(
    private val jwtAuthFilter: JwtAuthFilter,
    private val authenticationEntryPoint: RestAuthenticationEntryPoint,
    private val accessDeniedHandler: RestAccessDeniedHandler,
) {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            // Sem estado e sem formulario: a API não usa cookie de sessão, então
            // CSRF não se aplica.
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .exceptionHandling {
                it.authenticationEntryPoint(authenticationEntryPoint)
                it.accessDeniedHandler(accessDeniedHandler)
            }
            .authorizeHttpRequests { autorizar ->
                autorizar
                    // ---------------------------------------- rotas publicas
                    .requestMatchers("/auth/login", "/auth/refresh").permitAll()
                    .requestMatchers(HttpMethod.POST, "/usuarios").permitAll()
                    // Verificação de e-mail e publica por definição: quem acabou de
                    // se cadastrar ainda não consegue logar, entao não teria como
                    // mandar token nenhum.
                    .requestMatchers(
                        HttpMethod.POST,
                        "/usuarios/verificacao",
                        "/usuarios/verificacao/reenvio",
                    ).permitAll()
                    .requestMatchers(HttpMethod.GET, "/unidades", "/unidades/*").permitAll()
                    .requestMatchers(HttpMethod.GET, "/unidades/*/cardapio").permitAll()
                    .requestMatchers(
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                    ).permitAll()
                    .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                    // O gateway não tem conta nesta API. A rota e liberada aqui e
                    // protegida por segredo combinado no header, dentro do controller.
                    .requestMatchers(HttpMethod.POST, "/pagamentos/callback").permitAll()

                    // ------------------------------------ rotas por perfil
                    .requestMatchers(HttpMethod.POST, "/usuarios/operadores")
                    .hasAnyRole(Perfil.ADMIN.name, Perfil.GERENTE.name)
                    // A trilha mostra o que cada pessoa fez na rede, entao ela
                    // propria e dado sensivel. So a matriz le.
                    .requestMatchers("/auditoria/**").hasRole(Perfil.ADMIN.name)

                    // Fidelidade e consentimento são do titular: so CLIENTE.
                    .requestMatchers("/fidelidade/**").hasRole(Perfil.CLIENTE.name)
                    .requestMatchers("/consentimentos/**").hasRole(Perfil.CLIENTE.name)

                    // Estoque, como o cardapio, vem antes das regras de /unidades.
                    // Do mais especifico para o mais generico, sempre.
                    .requestMatchers(HttpMethod.POST, "/unidades/*/estoque/movimentacoes")
                    .hasAnyRole(Perfil.ADMIN.name, Perfil.GERENTE.name, Perfil.ATENDENTE.name)
                    .requestMatchers(HttpMethod.GET, "/unidades/*/estoque/*/movimentacoes")
                    .hasAnyRole(Perfil.ADMIN.name, Perfil.GERENTE.name)
                    // Cozinha precisa ver saldo para saber o que da para preparar,
                    // mas não movimenta nada.
                    .requestMatchers(HttpMethod.GET, "/unidades/*/estoque")
                    .hasAnyRole(
                        Perfil.ADMIN.name,
                        Perfil.GERENTE.name,
                        Perfil.ATENDENTE.name,
                        Perfil.COZINHA.name,
                    )

                    // Cardapio vem ANTES das regras de /unidades. A ordem importa:
                    // o matcher de PUT em unidade e mais generico e, se viesse
                    // primeiro, engoliria o cardapio e exigiria ADMIN onde gerente
                    // deveria poder mexer.
                    .requestMatchers(HttpMethod.PUT, "/unidades/*/cardapio/*")
                    .hasAnyRole(Perfil.ADMIN.name, Perfil.GERENTE.name)
                    .requestMatchers(HttpMethod.DELETE, "/unidades/*/cardapio/*")
                    .hasAnyRole(Perfil.ADMIN.name, Perfil.GERENTE.name)

                    // Unidade em si e so da matriz. O padrão de caminho usa um
                    // segmento so, para não alcançar sub-recursos sem querer.
                    .requestMatchers(HttpMethod.POST, "/unidades").hasRole(Perfil.ADMIN.name)
                    .requestMatchers(HttpMethod.PUT, "/unidades/*").hasRole(Perfil.ADMIN.name)

                    // Pedido: cliente e atendente criam. O resto da visibilidade e das
                    // transições e decidido no service, que conhece o dono do pedido e
                    // a unidade dele.
                    .requestMatchers(HttpMethod.POST, "/pedidos")
                    .hasAnyRole(Perfil.CLIENTE.name, Perfil.ATENDENTE.name, Perfil.GERENTE.name, Perfil.ADMIN.name)
                    .requestMatchers(HttpMethod.PATCH, "/pedidos/*/status")
                    .hasAnyRole(
                        Perfil.ATENDENTE.name,
                        Perfil.COZINHA.name,
                        Perfil.GERENTE.name,
                        Perfil.ADMIN.name,
                    )

                    // Catalogo da rede: gerente ajuda a manter, mas so admin inativa.
                    .requestMatchers(HttpMethod.POST, "/produtos")
                    .hasAnyRole(Perfil.ADMIN.name, Perfil.GERENTE.name)
                    .requestMatchers(HttpMethod.PUT, "/produtos/*")
                    .hasAnyRole(Perfil.ADMIN.name, Perfil.GERENTE.name)
                    .requestMatchers(HttpMethod.DELETE, "/produtos/*").hasRole(Perfil.ADMIN.name)

                    // -------------------------------------------- o resto
                    // Qualquer rota nova que não esteja listada acima cai aqui e
                    // exige autenticação.
                    .anyRequest().authenticated()
            }
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }
}
