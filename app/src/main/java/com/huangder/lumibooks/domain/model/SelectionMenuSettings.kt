package com.huangder.lumibooks.domain.model

/**
 * Actions offered by the text selection menu.
 *
 * [storageKey] is persisted in user settings; never rename an existing key.
 * Declaration order is the default menu order.
 */
enum class SelectionMenuAction(val storageKey: String) {
    HIGHLIGHT("highlight"),
    UNDERLINE("underline"),
    NOTE("note"),
    DICTIONARY("dictionary"),
    EXCERPT("excerpt"),
    SEARCH("search"),
    WEB_SEARCH("web_search"),
    COPY("copy"),
    REPLACE("replace");

    companion object {
        private val byKey = entries.associateBy(SelectionMenuAction::storageKey)

        fun fromStorageKey(key: String): SelectionMenuAction? = byKey[key]
    }
}

/**
 * Order and visibility of the selection menu actions.
 *
 * Instances are immutable and always normalized: [order] lists every action exactly once and at
 * least one action stays visible. Two settings are equal only when both order and visibility
 * match, so a StateFlow emits a reorder even if the same actions remain visible.
 */
class SelectionMenuSettings private constructor(
    val order: List<SelectionMenuAction>,
    private val hidden: Set<SelectionMenuAction>
) {
    /** Default settings: declaration order, every action visible. */
    constructor() : this(SelectionMenuAction.entries.toList(), emptySet())

    fun isVisible(action: SelectionMenuAction): Boolean = action !in hidden

    /** False for the only remaining visible action; the menu must never become empty. */
    fun canHide(action: SelectionMenuAction): Boolean =
        isVisible(action) && order.count(::isVisible) > 1

    fun withVisibility(action: SelectionMenuAction, visible: Boolean): SelectionMenuSettings = when {
        visible == isVisible(action) -> this
        visible -> SelectionMenuSettings(order, hidden - action)
        canHide(action) -> SelectionMenuSettings(order, hidden + action)
        else -> this
    }

    /** Moves [action] by [steps] positions; a move past either end leaves the order unchanged. */
    fun moveBy(action: SelectionMenuAction, steps: Int): SelectionMenuSettings {
        val target = order.indexOf(action) + steps
        return if (target !in order.indices) this else moveTo(action, target)
    }

    fun moveTo(action: SelectionMenuAction, index: Int): SelectionMenuSettings {
        val reordered = order.toMutableList()
        reordered.remove(action)
        reordered.add(index.coerceIn(0, reordered.size), action)
        return withOrder(reordered)
    }

    /** Replaces the order; missing actions are appended in default order, duplicates dropped. */
    fun withOrder(newOrder: List<SelectionMenuAction>): SelectionMenuSettings = create(newOrder, hidden)

    /**
     * Actions to show for the current selection, in the configured order. Replace only applies to
     * TXT books, and an annotation type that already covers the selection is not offered again.
     * Falls back to Copy so the menu always has an action.
     */
    fun visibleActions(
        isTxt: Boolean,
        hasHighlight: Boolean,
        hasUnderline: Boolean
    ): List<SelectionMenuAction> = order.filter { action ->
        isVisible(action) && when (action) {
            SelectionMenuAction.REPLACE -> isTxt
            SelectionMenuAction.HIGHLIGHT -> !hasHighlight
            SelectionMenuAction.UNDERLINE -> !hasUnderline
            else -> true
        }
    }.ifEmpty { listOf(SelectionMenuAction.COPY) }

    override fun equals(other: Any?): Boolean =
        other is SelectionMenuSettings && order == other.order && hidden == other.hidden

    override fun hashCode(): Int = 31 * order.hashCode() + hidden.hashCode()

    override fun toString(): String =
        "SelectionMenuSettings(" + order.joinToString { if (isVisible(it)) it.storageKey else "-${it.storageKey}" } + ")"

    companion object {
        /**
         * Builds normalized settings from possibly incomplete input, e.g. persisted data written
         * by an older version. If every action would be hidden, Copy stays visible.
         */
        fun create(
            order: List<SelectionMenuAction>,
            hidden: Set<SelectionMenuAction>
        ): SelectionMenuSettings {
            val completeOrder = LinkedHashSet(order).apply { addAll(SelectionMenuAction.entries) }.toList()
            val effectiveHidden = if (hidden.containsAll(completeOrder)) {
                hidden - SelectionMenuAction.COPY
            } else hidden
            return SelectionMenuSettings(completeOrder, effectiveHidden.toSet())
        }
    }
}
