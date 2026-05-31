package com.example.lowlevelide.terminal

import android.graphics.Color

/**
 * Colour palettes for the terminal pane.
 *
 * The Termux [com.termux.view.TerminalView] is a plain Android [android.view.View],
 * so we always control the view background colour. When the bundled
 * `terminal-emulator` build also exposes the optional `TerminalColors` API the
 * [com.example.lowlevelide.ui.terminal.TerminalFragment] applies the full 16-colour
 * ANSI table via reflection; otherwise the background/foreground pair below still
 * gives each palette a distinct look.
 */
data class TerminalPalette(
    val key: String,
    val background: Int,
    val foreground: Int,
    /** 16-entry ANSI colour table (0-7 normal, 8-15 bright). */
    val ansi: IntArray
) {
    companion object {

        private fun c(hex: String): Int = Color.parseColor(hex)

        val GITHUB_DARK = TerminalPalette(
            "github-dark", c("#0D1117"), c("#E6EDF3"),
            intArrayOf(
                c("#484F58"), c("#FF7B72"), c("#3FB950"), c("#D29922"),
                c("#58A6FF"), c("#BC8CFF"), c("#39C5CF"), c("#B1BAC4"),
                c("#6E7681"), c("#FFA198"), c("#56D364"), c("#E3B341"),
                c("#79C0FF"), c("#D2A8FF"), c("#56D4DD"), c("#F0F6FC")
            )
        )

        val SOLARIZED_DARK = TerminalPalette(
            "solarized-dark", c("#002B36"), c("#839496"),
            intArrayOf(
                c("#073642"), c("#DC322F"), c("#859900"), c("#B58900"),
                c("#268BD2"), c("#D33682"), c("#2AA198"), c("#EEE8D5"),
                c("#002B36"), c("#CB4B16"), c("#586E75"), c("#657B83"),
                c("#839496"), c("#6C71C4"), c("#93A1A1"), c("#FDF6E3")
            )
        )

        val DRACULA = TerminalPalette(
            "dracula", c("#282A36"), c("#F8F8F2"),
            intArrayOf(
                c("#21222C"), c("#FF5555"), c("#50FA7B"), c("#F1FA8C"),
                c("#BD93F9"), c("#FF79C6"), c("#8BE9FD"), c("#F8F8F2"),
                c("#6272A4"), c("#FF6E6E"), c("#69FF94"), c("#FFFFA5"),
                c("#D6ACFF"), c("#FF92DF"), c("#A4FFFF"), c("#FFFFFF")
            )
        )

        val GRUVBOX_DARK = TerminalPalette(
            "gruvbox-dark", c("#282828"), c("#EBDBB2"),
            intArrayOf(
                c("#282828"), c("#CC241D"), c("#98971A"), c("#D79921"),
                c("#458588"), c("#B16286"), c("#689D6A"), c("#A89984"),
                c("#928374"), c("#FB4934"), c("#B8BB26"), c("#FABD2F"),
                c("#83A598"), c("#D3869B"), c("#8EC07C"), c("#EBDBB2")
            )
        )

        val NORD = TerminalPalette(
            "nord", c("#2E3440"), c("#D8DEE9"),
            intArrayOf(
                c("#3B4252"), c("#BF616A"), c("#A3BE8C"), c("#EBCB8B"),
                c("#81A1C1"), c("#B48EAD"), c("#88C0D0"), c("#E5E9F0"),
                c("#4C566A"), c("#BF616A"), c("#A3BE8C"), c("#EBCB8B"),
                c("#81A1C1"), c("#B48EAD"), c("#8FBCBB"), c("#ECEFF4")
            )
        )

        val LIGHT = TerminalPalette(
            "light", c("#FFFFFF"), c("#1F2328"),
            intArrayOf(
                c("#24292F"), c("#CF222E"), c("#116329"), c("#4D2D00"),
                c("#0969DA"), c("#8250DF"), c("#1B7C83"), c("#6E7781"),
                c("#57606A"), c("#A40E26"), c("#1A7F37"), c("#633C01"),
                c("#218BFF"), c("#A475F9"), c("#3192AA"), c("#8C959F")
            )
        )

        private val ALL = listOf(
            GITHUB_DARK, SOLARIZED_DARK, DRACULA, GRUVBOX_DARK, NORD, LIGHT
        ).associateBy { it.key }

        fun byKey(key: String?): TerminalPalette = ALL[key] ?: GITHUB_DARK
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TerminalPalette) return false
        return key == other.key
    }

    override fun hashCode(): Int = key.hashCode()
}
