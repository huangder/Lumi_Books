package com.huangder.lumibooks.ui.reader

import androidx.lifecycle.SavedStateHandle
import com.huangder.lumibooks.tts.ListeningDisplayStyle

/** Temporary cover/lyrics toggles survive recreation without changing the user's default. */
internal class ListeningDisplayMode(private val savedState: SavedStateHandle) {
    // getStateFlow inserts its initial value, so detect restoration before creating the flow.
    private val restored = savedState.contains("subtitles")
    private var previousDefault: ListeningDisplayStyle? = null
    val subtitles = savedState.getStateFlow("subtitles", false)

    fun applyDefault(style: ListeningDisplayStyle) {
        if (previousDefault == style) return
        if (previousDefault != null || !restored) {
            savedState["subtitles"] = style == ListeningDisplayStyle.SUBTITLES
        }
        previousDefault = style
    }

    fun toggle() { savedState["subtitles"] = !subtitles.value }
}
