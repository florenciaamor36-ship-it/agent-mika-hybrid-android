package com.past9.phoneaos.mika

import com.past9.phoneaos.screen.ScreenBlocklist

/** Pure finance-app policy shared by the Mika-style visible-screen tools and unit tests. */
object MikaScreenPolicy {
    fun mayRead(blocklist: ScreenBlocklist, packageName: String, label: String = ""): Boolean =
        blocklist.blocked(packageName, label) == null

    fun mayAct(blocklist: ScreenBlocklist, packageName: String, label: String = ""): Boolean =
        blocklist.blocked(packageName, label) == null && !blocklist.isExplicitlyAllowedWallet(packageName)
}
