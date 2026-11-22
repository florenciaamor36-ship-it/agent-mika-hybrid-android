package com.past9.phoneaos.mika

import android.content.Context
import android.content.Intent
import com.past9.phoneaos.agent.Tool
import com.past9.phoneaos.agent.ToolContext
import com.past9.phoneaos.agent.ToolSpec
import com.past9.phoneaos.agent.int
import com.past9.phoneaos.agent.schema
import com.past9.phoneaos.agent.str
import com.past9.phoneaos.screen.ScreenBlocklist
import com.past9.phoneaos.screen.ScreenRules
import com.past9.phoneaos.screen.ScreenElement
import com.past9.phoneaos.screen.Screens
import org.json.JSONObject

private fun serviceOrMessage(): Pair<MikaAccessibilityService?, String?> {
    val service = MikaAccessibilityService.current()
    return if (service == null) null to "The optional accessibility controller is enabled in Android settings but is not connected yet. Reopen the app and try again." else service to null
}
private fun accessPolicy(context: Context, pkg: String): Triple<ScreenBlocklist, String, String> {
    val label = runCatching { val pm = context.packageManager; pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
    val list = ScreenBlocklist(Screens.builtInBlocklist(context), Screens.blockAdded(context), Screens.walletAccess(context))
    val reason = if (list.isExplicitlyAllowedWallet(pkg)) "$label is opted in as read-only; this controller cannot operate it." else ScreenBlocklist.reason(label)
    return Triple(list, label, reason)
}
private fun currentSnapshot(context: Context): Pair<MikaAccessibilityService, MikaAccessibilityService.Snapshot>? {
    val service = MikaAccessibilityService.current() ?: return null
    val snapshot = service.snapshot() ?: return null
    return service to snapshot
}
private fun nodeElement(n: MikaAccessibilityService.Node) = ScreenElement(
    id = n.id, cls = n.role, text = n.text, desc = "", hint = n.hint, res = n.resourceId,
    left = n.bounds.left, top = n.bounds.top, right = n.bounds.right, bottom = n.bounds.bottom,
    flags = buildString { if (n.clickable) append('c'); if (n.editable) append('e'); if (n.scrollable) append('s') }, window = 0,
)

class MikaScreenReadTool(private val context: Context) : Tool {
    override val spec = ToolSpec("mika_screen_read",
        "Read text and accessible controls from the user's CURRENT visible screen using the optional Android accessibility service. Read only: it does not tap, type, store screenshots or keep event text. The output names the foreground app, a generation number and numbered elements; those IDs expire whenever the screen changes.", schema())
    override suspend fun run(input: JSONObject, ctx: ToolContext): String {
        if (!MikaAccessibilityService.enabled(context)) return "Mika-style screen control is off. Ask the user to enable it in Settings > Background screen > Accessibility, then retry."
        val (svc, shot) = currentSnapshot(context) ?: return "Accessibility is enabled but Android has not connected the service. Nothing was read."
        val (policy, label, reason) = accessPolicy(context, shot.packageName)
        if (!MikaScreenPolicy.mayRead(policy, shot.packageName, label)) return reason
        val label = runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(shot.packageName, 0)).toString() }.getOrDefault(shot.packageName)
        val lines = shot.nodes.map { n ->
            val kind = when { n.password -> "password field (text hidden)"; n.editable -> "field"; n.clickable -> "button"; n.scrollable -> "list"; else -> "text" }
            val text = if (n.password) "" else n.text.ifBlank { n.hint }
            "[${n.id}] $kind${if (text.isBlank()) "" else " \"${text.replace('\n',' ').take(140)}\""}"
        }
        return buildString {
            append("Visible app: ").append(label).append(" (package ").append(shot.packageName).append("). Generation ").append(shot.generation).append(". Read-only snapshot; IDs expire after any screen change.\n")
            append(lines.take(250).joinToString("\n").ifBlank { "No accessible text or controls were exposed by this screen." })
        }
    }
}

class MikaScreenOpenTool(private val context: Context) : Tool {
    override val spec = ToolSpec("mika_screen_open_app",
        "Open an installed app on the user's visible phone screen (not the hidden background display). Banking, payment, crypto and wallet apps remain blocked unless a wallet has its exact read-only opt-in; even then this tool only opens it for the user to inspect, and agent interaction is disabled.",
        schema(listOf("package"), "package" to str("Exact installed package name")))
    override suspend fun run(input: JSONObject, ctx: ToolContext): String {
        if (!MikaAccessibilityService.enabled(context)) return "Mika-style screen control is off. The user must enable the accessibility service in Android settings first."
        val pkg = input.optString("package").trim(); if (pkg.isBlank()) return "Provide an installed app's package name."
        val (policy, label, reason) = accessPolicy(context, pkg)
        if (!MikaScreenPolicy.mayRead(policy, pkg, label)) return reason
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return "No launchable installed app with package $pkg was found."
        return runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "Opened $pkg on the visible phone screen. The agent will not interact with a finance app or opted-in wallet; the user can take over and operate it manually."
        }.getOrElse { "Android refused to open $pkg: ${it.message ?: "unknown error"}." }
    }
}

class MikaScreenTapTool(private val context: Context) : Tool {
    override val spec = ToolSpec("mika_screen_tap",
        "Tap a numbered control from the most recent mika_screen_read result on the CURRENT visible screen. Pass its generation too; if the screen changed, read again. Protected finance apps and read-only wallets cannot be operated. Buttons that send, pay, delete, publish or transfer require the user's approval.",
        schema(listOf("element", "generation"), "element" to int("Number from the latest screen read"), "generation" to int("Generation number from that same screen read")))
    override suspend fun run(input: JSONObject, ctx: ToolContext): String {
        if (!MikaAccessibilityService.enabled(context)) return "Mika-style screen control is off; nothing was tapped."
        val (svc, shot) = currentSnapshot(context) ?: return "Accessibility service is not connected; nothing was tapped."
        if (shot.generation != input.optLong("generation", -1)) return "The screen changed since the last read. Nothing was tapped; call mika_screen_read again."
        val node = shot.nodes.firstOrNull { it.id == input.optInt("element", -1) } ?: return "No such visible element. Read the screen again."
        val (policy, label, reason) = accessPolicy(context, shot.packageName)
        if (!MikaScreenPolicy.mayRead(policy, shot.packageName, label)) return reason
        if (!MikaScreenPolicy.mayAct(policy, shot.packageName, label)) return reason
        if (!node.enabled || node.password) return "That control is disabled or sensitive; nothing was tapped."
        val element = nodeElement(node)
        val gate = ScreenRules.beforePress(shot.packageName, ScreenRules.Rule.ASK, element, node.text.ifBlank { node.hint }, emptyList())
        if (gate is ScreenRules.Gate.Refuse) return gate.reason
        if (gate is ScreenRules.Gate.Ask) {
            val answer = ctx.ask("APPROVAL|${gate.what}|${gate.details}", listOf("Approve", "Decline"))
            if (answer != "Approve") return if (answer == "Decline") "Not done; you declined." else "Not done; no approval arrived."
        }
        if (!svc.click(node.path, shot.generation)) return "The screen changed or Android refused the tap. Nothing else was done; read the screen again."
        return "Tapped the selected control in the visible app. Read the screen again before the next action."
    }
}

class MikaScreenTypeTool(private val context: Context) : Tool {
    override val spec = ToolSpec("mika_screen_type",
        "Type into a visible non-password text field from the latest mika_screen_read result. Pass the matching generation. Finance apps, read-only wallets and password fields are never typed into.",
        schema(listOf("element", "generation", "text"), "element" to int("Editable field number from latest read"), "generation" to int("Generation number from same read"), "text" to str("Text to enter; never provide passwords, PINs or payment credentials")))
    override suspend fun run(input: JSONObject, ctx: ToolContext): String {
        if (!MikaAccessibilityService.enabled(context)) return "Mika-style screen control is off; nothing was typed."
        val (svc, shot) = currentSnapshot(context) ?: return "Accessibility service is not connected; nothing was typed."
        if (shot.generation != input.optLong("generation", -1)) return "The screen changed since the last read. Nothing was typed; call mika_screen_read again."
        val node = shot.nodes.firstOrNull { it.id == input.optInt("element", -1) } ?: return "No such visible field. Read the screen again."
        val (policy, label, reason) = accessPolicy(context, shot.packageName)
        if (!MikaScreenPolicy.mayRead(policy, shot.packageName, label)) return reason
        if (!MikaScreenPolicy.mayAct(policy, shot.packageName, label)) return reason
        if (!node.editable || node.password || !node.enabled) return "That is not an enabled, non-password field; nothing was typed."
        val text = input.optString("text")
        if (text.length > 2_000) return "Text is too long; nothing was typed."
        if (!svc.type(node.path, shot.generation, text)) return "The screen changed or Android refused the text. Nothing else was done; read again."
        return "Typed into the selected field. Read the screen again before continuing."
    }
}

fun mikaScreenTools(context: Context): List<Tool> = listOf(MikaScreenReadTool(context), MikaScreenOpenTool(context), MikaScreenTapTool(context), MikaScreenTypeTool(context))
