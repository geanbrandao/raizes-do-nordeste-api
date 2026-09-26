package com.geanbrandao.raizes.api.domain

/**
 * Formato de operação da unidade.
 *
 * Nem toda loja da rede e igual: uma tem cozinha completa, outra opera reduzida.
 * Isso explica por que o cardapio e por unidade e não por rede.
 */
enum class TipoOperacaoUnidade {

    /** Cozinha completa, prepara o cardapio inteiro. */
    COMPLETA,

    /** Operação reduzida, prepara so parte do cardapio. */
    REDUZIDA,
}
