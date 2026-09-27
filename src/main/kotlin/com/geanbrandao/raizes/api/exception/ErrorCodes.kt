package com.geanbrandao.raizes.api.exception

/**
 * Lista central de codigos de erro da API.
 *
 * O cliente decide o que mostrar olhando esse codigo, nunca o texto da mensagem,
 * que pode mudar a qualquer momento. Codigo novo se declara aqui antes de usar,
 * senão em pouco tempo cada canto da API inventa o proprio nome para o mesmo erro.
 */
object ErrorCodes {

    // Genericos
    const val VALIDACAO = "VALIDACAO"
    const val REQUISICAO_INVALIDA = "REQUISICAO_INVALIDA"
    const val NAO_ENCONTRADO = "NAO_ENCONTRADO"
    const val CONFLITO = "CONFLITO"
    const val ERRO_INTERNO = "ERRO_INTERNO"
    const val LIMITE_DE_REQUISICOES = "LIMITE_DE_REQUISICOES"

    // Autenticação e autorização
    const val NAO_AUTENTICADO = "NAO_AUTENTICADO"
    const val CREDENCIAIS_INVALIDAS = "CREDENCIAIS_INVALIDAS"
    const val SEM_PERMISSAO = "SEM_PERMISSAO"
    const val TOKEN_INVALIDO = "TOKEN_INVALIDO"
    const val TOKEN_EXPIRADO = "TOKEN_EXPIRADO"
    const val USUARIO_INATIVO = "USUARIO_INATIVO"

    // Usuario
    /**
     * So usado no cadastro de operador, que e autenticado e feito por admin ou
     * gerente. O cadastro publico de cliente nunca devolve este codigo, senão
     * viraria uma forma de descobrir quais e-mails tem conta na rede.
     */
    const val EMAIL_JA_CADASTRADO = "EMAIL_JA_CADASTRADO"
    const val EMAIL_NAO_VERIFICADO = "EMAIL_NAO_VERIFICADO"
    const val CODIGO_VERIFICACAO_INVALIDO = "CODIGO_VERIFICACAO_INVALIDO"

    // Unidade, produto e cardapio
    const val UNIDADE_NAO_ENCONTRADA = "UNIDADE_NAO_ENCONTRADA"
    const val UNIDADE_INATIVA = "UNIDADE_INATIVA"
    const val PRODUTO_NAO_ENCONTRADO = "PRODUTO_NAO_ENCONTRADO"
    const val PRODUTO_FORA_DO_CARDAPIO = "PRODUTO_FORA_DO_CARDAPIO"
    const val PRODUTO_INDISPONIVEL = "PRODUTO_INDISPONIVEL"

    // Estoque
    const val ESTOQUE_INSUFICIENTE = "ESTOQUE_INSUFICIENTE"
    const val ESTOQUE_NAO_ENCONTRADO = "ESTOQUE_NAO_ENCONTRADO"

    // Pedido
    const val PEDIDO_NAO_ENCONTRADO = "PEDIDO_NAO_ENCONTRADO"
    const val TRANSICAO_DE_STATUS_INVALIDA = "TRANSICAO_DE_STATUS_INVALIDA"
    const val PEDIDO_NAO_PODE_SER_CANCELADO = "PEDIDO_NAO_PODE_SER_CANCELADO"
    const val CANAL_PEDIDO_INVALIDO = "CANAL_PEDIDO_INVALIDO"

    // Pagamento
    const val PAGAMENTO_NAO_ENCONTRADO = "PAGAMENTO_NAO_ENCONTRADO"
    const val PAGAMENTO_RECUSADO = "PAGAMENTO_RECUSADO"
    const val PEDIDO_JA_PAGO = "PEDIDO_JA_PAGO"
    const val GATEWAY_INDISPONIVEL = "GATEWAY_INDISPONIVEL"

    // Fidelidade e LGPD
    const val CONTA_FIDELIDADE_NAO_ENCONTRADA = "CONTA_FIDELIDADE_NAO_ENCONTRADA"
    const val PONTOS_INSUFICIENTES = "PONTOS_INSUFICIENTES"
    const val CONSENTIMENTO_NECESSARIO = "CONSENTIMENTO_NECESSARIO"
    const val CONSENTIMENTO_NAO_ENCONTRADO = "CONSENTIMENTO_NAO_ENCONTRADO"
}
