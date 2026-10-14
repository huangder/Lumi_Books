package com.huangder.lumibooks.domain.model

enum class SelectionMenuAction(val key: String) {
    HIGHLIGHT("highlight"), UNDERLINE("underline"), NOTE("note"),
    DICTIONARY("dictionary"), EXCERPT("excerpt"),
    SEARCH("search"), WEB_SEARCH("web_search"), COPY("copy"), REPLACE("replace")
}

/** A list (rather than map iteration order) makes order changes observable by StateFlow. */
data class SelectionMenuSettings(
    val order: List<SelectionMenuAction> = SelectionMenuAction.entries.toList(),
    val hidden: Set<SelectionMenuAction> = emptySet()
) {
    fun isVisible(action: SelectionMenuAction): Boolean = action !in hidden

    fun withVisibility(action: SelectionMenuAction, visible: Boolean): SelectionMenuSettings {
        if (!visible && isVisible(action) && order.count(::isVisible) <= 1) return this
        return copy(hidden = if (visible) hidden - action else hidden + action)
    }

    fun move(action: SelectionMenuAction, direction: Int): SelectionMenuSettings {
        val index = order.indexOf(action)
        val target = index + direction
        if (index < 0 || target !in order.indices) return this
        return copy(order = order.toMutableList().apply { add(target, removeAt(index)) })
    }

    fun visibleActions(
        isTxt: Boolean = true,
        hasHighlight: Boolean = false,
        hasUnderline: Boolean = false
    ): List<SelectionMenuAction> = order.filter {
        isVisible(it) && when (it) {
            SelectionMenuAction.REPLACE -> isTxt
            SelectionMenuAction.HIGHLIGHT -> !hasHighlight
            SelectionMenuAction.UNDERLINE -> !hasUnderline
            else -> true
        }
    }.ifEmpty { listOf(SelectionMenuAction.COPY) }

    companion object {
        fun fromStored(order: List<String>, visibility: Map<String, Boolean>): SelectionMenuSettings {
            val known = SelectionMenuAction.entries.associateBy { it.key }
            val normalizedOrder = (order.mapNotNull(known::get) + SelectionMenuAction.entries).distinct()
            val hidden = SelectionMenuAction.entries.filter { visibility[it.key] == false }.toSet()
            return SelectionMenuSettings(normalizedOrder,
                if (hidden.size == SelectionMenuAction.entries.size) hidden - SelectionMenuAction.COPY else hidden)
        }
    }
}
