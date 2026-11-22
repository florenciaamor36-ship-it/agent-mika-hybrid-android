package com.past9.phoneaos.mika

import com.past9.phoneaos.screen.ScreenBlocklist

/** Pure finance-app policy shared by the Mika-style visible-screen tools and unit tests. */
object MikaScreenPolicy {
    /** Spanish transactional labels that must trigger the same explicit action confirmation as English labels. */
    val RISKY_WORDS = listOf("enviar", "publicar", "responder", "compartir", "reenviar", "pagar", "pago", "comprar", "compra", "realizar compra", "pedido", "transferir", "transferencia", "retirar", "depositar", "eliminar", "borrar", "confirmar", "autorizar", "suscribir")

    fun mayRead(blocklist: ScreenBlocklist, packageName: String, label: String = ""): Boolean =
        blocklist.blocked(packageName, label) == null

    fun mayAct(blocklist: ScreenBlocklist, packageName: String, label: String = ""): Boolean =
        blocklist.blocked(packageName, label) == null && !blocklist.isExplicitlyAllowedWallet(packageName)
}
