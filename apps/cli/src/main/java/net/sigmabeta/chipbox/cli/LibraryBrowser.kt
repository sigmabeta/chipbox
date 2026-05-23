package net.sigmabeta.chipbox.cli

import com.github.ajalt.mordant.input.interactiveSelectList
import com.github.ajalt.mordant.terminal.Terminal

/**
 * Mordant-driven menu navigator over a tree of [MenuNode]s. A node with children opens a new
 * screen; a node with an [MenuNode.onSelect] action runs it (and may end navigation); a plain leaf
 * does nothing. Back / q / Esc returns one level — at the top level it returns to the caller.
 */
class LibraryBrowser(private val terminal: Terminal) {
    fun browse(
        root: List<MenuNode>,
        title: String = LIBRARY_TITLE,
        exitLabel: String = QUIT_ROW,
    ) = showMenu(title, root, exitLabel)

    private fun showMenu(title: String, nodes: List<MenuNode>, exitRow: String) {
        val nodesByLabel = nodes.associateBy { it.label }
        val rows = buildRows(exitRow, nodes.map { it.label })

        var exit = false
        while (!exit) {
            val choice = terminal.interactiveSelectList(rows, title = title)
            exit = when {
                choice == null || choice == exitRow -> true
                else -> handleSelection(nodesByLabel[choice])
            }
        }
    }

    // Runs a node's action (which decides whether to end navigation), drills into its children, or
    // does nothing for a leaf / the "more" note. Returns true to leave the current screen.
    private fun handleSelection(node: MenuNode?): Boolean {
        val action = node?.onSelect
        if (action != null) return action()
        val children = node?.children
        if (children != null) showMenu(node.label, children(), BACK_ROW)
        return false
    }

    // The select list has no scroll viewport, so cap rows to what fits the terminal height and note
    // the remainder rather than overflowing the screen.
    private fun buildRows(exitRow: String, labels: List<String>): List<String> {
        val maxItems = (terminal.size.height - RESERVED_ROWS).coerceAtLeast(MIN_VISIBLE_ITEMS)
        val shown = labels.take(maxItems)
        val overflow = labels.size - shown.size
        val moreNote = if (overflow > 0) "… $overflow more not shown" else null
        return listOf(exitRow) + shown + listOfNotNull(moreNote)
    }

    private companion object {
        const val LIBRARY_TITLE = "Chipbox library"
        const val QUIT_ROW = "Quit"
        const val BACK_ROW = "← Back"
        const val RESERVED_ROWS = 6
        const val MIN_VISIBLE_ITEMS = 5
    }
}

/**
 * One selectable row. [children] lazily produces the next screen (queried on demand); [onSelect]
 * runs an action and returns true to end navigation. A node with neither is an inert leaf.
 */
class MenuNode(
    val label: String,
    val onSelect: (() -> Boolean)? = null,
    val children: (() -> List<MenuNode>)? = null,
)
