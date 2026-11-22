package com.past9.phoneaos.mika

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.graphics.Rect
import android.graphics.Path
import android.os.Bundle
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A small, opt-in accessibility bridge inspired by Mika. It stores no event text or screenshots. */
class MikaAccessibilityService : AccessibilityService() {
    data class Node(
        val id: Int,
        val path: List<Int>,
        val role: String,
        val text: String,
        val hint: String,
        val resourceId: String,
        val bounds: Rect,
        val clickable: Boolean,
        val editable: Boolean,
        val password: Boolean,
        val scrollable: Boolean,
        val enabled: Boolean,
    )
    data class Snapshot(val packageName: String, val generation: Long, val nodes: List<Node>)

    override fun onServiceConnected() { super.onServiceConnected(); instance = this; generation.incrementAndGet() }
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Track only that the screen changed. Never retain event text, notification text or contents.
        if (event.eventType in setOf(
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                AccessibilityEvent.TYPE_WINDOWS_CHANGED,
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                AccessibilityEvent.TYPE_VIEW_SCROLLED,
                AccessibilityEvent.TYPE_VIEW_CLICKED,
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
                AccessibilityEvent.TYPE_VIEW_SELECTED,
                AccessibilityEvent.TYPE_VIEW_FOCUSED,
                AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED,
            )) generation.incrementAndGet()
    }
    override fun onInterrupt() = Unit
    override fun onUnbind(intent: Intent?): Boolean { if (instance === this) instance = null; return super.onUnbind(intent) }
    override fun onDestroy() { if (instance === this) instance = null; super.onDestroy() }

    suspend fun snapshot(): Snapshot? = withContext(Dispatchers.Main.immediate) {
        val before = generation.get()
        val root = rootInActiveWindow ?: return@withContext null
        val pkg = root.packageName?.toString().orEmpty()
        val seen = ArrayList<Node>()
        try { collect(root, emptyList(), seen, 0) } finally { root.recycle() }
        // Never label a mixed/changed tree as fresh: actions only accept an unchanged generation.
        if (generation.get() != before) null else Snapshot(pkg, before, seen)
    }

    private fun collect(node: AccessibilityNodeInfo, path: List<Int>, out: MutableList<Node>, depth: Int) {
        if (out.size >= 400 || depth > 32) return
        val password = node.isPassword
        val text = if (password) "" else node.text?.toString().orEmpty().take(500)
        val desc = if (password) "" else node.contentDescription?.toString().orEmpty().take(300)
        val label = text.ifBlank { desc }
        if (label.isNotBlank() || node.isClickable || node.isEditable || node.isScrollable) {
            val rect = Rect(); node.getBoundsInScreen(rect)
            out += Node(out.size, path, node.className?.toString().orEmpty().substringAfterLast('.'), label,
                if (password) "" else node.hintText?.toString().orEmpty().take(200), node.viewIdResourceName.orEmpty(), rect,
                node.isClickable, node.isEditable, password, node.isScrollable, node.isEnabled)
        }
        val count = node.childCount
        for (i in 0 until count) {
            val child = node.getChild(i) ?: continue
            try { collect(child, path + i, out, depth + 1) } finally { child.recycle() }
        }
    }

    suspend fun click(path: List<Int>, expectedGeneration: Long): Boolean = withContext(Dispatchers.Main.immediate) { onFreshNode(path, expectedGeneration) { node ->
        if (!node.isEnabled || node.isPassword) false else {
            if (node.isClickable) return@onFreshNode node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            var target = node.parent
            while (target != null) {
                if (target.isClickable) {
                    val ok = target.isEnabled && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    target.recycle()
                    return@onFreshNode ok
                }
                val parent = target.parent
                target.recycle()
                target = parent
            }
            false
        }
    } }

    suspend fun type(path: List<Int>, expectedGeneration: Long, value: String): Boolean = withContext(Dispatchers.Main.immediate) { onFreshNode(path, expectedGeneration) { node ->
        if (!node.isEnabled || !node.isEditable || node.isPassword) false else {
            val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value) }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }
    } }

    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, expectedGeneration: Long): Boolean {
        return withContext(Dispatchers.Main.immediate) {
        if (generation.get() != expectedGeneration || x1 < 0 || y1 < 0 || x2 < 0 || y2 < 0) return@withContext false
        val dm = resources.displayMetrics
        if (x1 >= dm.widthPixels || x2 >= dm.widthPixels || y1 >= dm.heightPixels || y2 >= dm.heightPixels) return@withContext false
        val path = Path().apply { moveTo(x1.toFloat(), y1.toFloat()); lineTo(x2.toFloat(), y2.toFloat()) }
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 350))
            .build()
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            if (generation.get() != expectedGeneration) { cont.resume(false); return@suspendCancellableCoroutine }
            val accepted = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(description: android.accessibilityservice.GestureDescription) { if (cont.isActive) cont.resume(true) }
                override fun onCancelled(description: android.accessibilityservice.GestureDescription) { if (cont.isActive) cont.resume(false) }
            }, null)
            if (!accepted && cont.isActive) cont.resume(false)
        }
        }
    }

    suspend fun pressBack(): Boolean = withContext(Dispatchers.Main.immediate) { performGlobalAction(GLOBAL_ACTION_BACK) }

    private inline fun onFreshNode(path: List<Int>, expectedGeneration: Long, action: (AccessibilityNodeInfo) -> Boolean): Boolean {
        if (generation.get() != expectedGeneration) return false
        var current = rootInActiveWindow ?: return false
        try {
            for (index in path) {
                val child = current.getChild(index) ?: return false
                current.recycle(); current = child
            }
            if (generation.get() != expectedGeneration) return false
            return action(current)
        } finally { current.recycle() }
    }

    companion object {
        @Volatile private var instance: MikaAccessibilityService? = null
        private val generation = AtomicLong(0)
        fun current(): MikaAccessibilityService? = instance
        fun component(context: Context) = ComponentName(context, MikaAccessibilityService::class.java)
        fun enabled(context: Context): Boolean {
            val expected = component(context).flattenToString()
            val raw = runCatching { Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) }.getOrNull().orEmpty()
            return raw.split(':').any { it.equals(expected, true) }
        }
        fun openSettings(context: Context) {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
