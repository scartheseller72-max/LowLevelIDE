package com.example.lowlevelide.ui.terminal

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.example.lowlevelide.R

/**
 * The extra-keys helper bar shown above the soft keyboard. Provides single-tap access to
 * keys that on-screen keyboards usually don't expose: Esc, Tab, Ctrl, Alt, arrows, common
 * punctuation. CTRL/ALT toggle as modifiers for the next keypress.
 *
 * Wire up by passing a write-to-session callback to [attach]. Keeping the dependency
 * abstract avoids touching any package-private Termux internals.
 */
class ExtraKeysView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    var ctrlActive: Boolean = false
        private set
    var altActive: Boolean = false
        private set

    private var sender: ((String) -> Unit)? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_extra_keys, this, true)
        val container = findViewById<LinearLayout>(R.id.extraKeysContainer)
        KEYS.forEach { key -> container.addView(makeKey(key)) }
    }

    fun attach(send: (String) -> Unit) { sender = send }

    private fun makeKey(key: KeyDef): View {
        val tv = TextView(context).apply {
            text = key.label
            setPadding(20, 6, 20, 6)
            setTextColor(0xFFECEFF1.toInt())
            textSize = 14f
            isClickable = true
            isFocusable = true
            setBackgroundResource(android.R.drawable.btn_default)
        }
        val params = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT).apply {
            marginStart = 4; marginEnd = 4
        }
        tv.layoutParams = params
        tv.setOnClickListener {
            when (key.action) {
                Action.CTRL -> { ctrlActive = !ctrlActive; tv.alpha = if (ctrlActive) 1f else 0.6f }
                Action.ALT -> { altActive = !altActive; tv.alpha = if (altActive) 1f else 0.6f }
                Action.SEND -> {
                    val seq = key.sequence ?: key.label
                    sender?.invoke(seq)
                }
            }
        }
        if (key.action == Action.CTRL || key.action == Action.ALT) tv.alpha = 0.6f
        return tv
    }

    companion object {
        private val KEYS = listOf(
            KeyDef("ESC", Action.SEND, "\u001b"),
            KeyDef("/",   Action.SEND, "/"),
            KeyDef("-",   Action.SEND, "-"),
            KeyDef("_",   Action.SEND, "_"),
            KeyDef("|",   Action.SEND, "|"),
            KeyDef("CTRL", Action.CTRL),
            KeyDef("ALT",  Action.ALT),
            KeyDef("TAB", Action.SEND, "\t"),
            KeyDef("←",   Action.SEND, "\u001b[D"),
            KeyDef("↓",   Action.SEND, "\u001b[B"),
            KeyDef("↑",   Action.SEND, "\u001b[A"),
            KeyDef("→",   Action.SEND, "\u001b[C"),
            KeyDef("HOME", Action.SEND, "\u001b[H"),
            KeyDef("END",  Action.SEND, "\u001b[F"),
            KeyDef("PGUP", Action.SEND, "\u001b[5~"),
            KeyDef("PGDN", Action.SEND, "\u001b[6~"),
            KeyDef("`",   Action.SEND, "`"),
            KeyDef("~",   Action.SEND, "~"),
            KeyDef("\\",  Action.SEND, "\\"),
            KeyDef("\"",  Action.SEND, "\""),
            KeyDef("'",   Action.SEND, "'")
        )
    }

    private enum class Action { SEND, CTRL, ALT }
    private data class KeyDef(val label: String, val action: Action, val sequence: String? = null)
}
