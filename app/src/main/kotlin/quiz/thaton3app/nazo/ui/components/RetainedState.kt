package quiz.thaton3app.nazo.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

/**
 * State that survives the portrait <-> landscape LAYOUT SWAP.
 *
 * Rotating does not recreate the activity (the manifest declares
 * `configChanges="orientation|screenSize|..."`), but it does swap an entire
 * composable subtree: portrait renders every screen inside one full-screen
 * `AnimatedContent`, while landscape Settings renders a master/detail `Row`.
 * A screen composed in one branch is removed and re-created at a different
 * position in the other, so every plain `remember` inside it is thrown away.
 *
 * That is why an open Backup or Restore preview vanished on rotation, while
 * the Home screen's "Switch API Key" sheet survived: Home sits in the same
 * branch in both orientations, so its subtree is never swapped.
 *
 * [rememberRetained] stores the value in a map that lives ABOVE the swap, so
 * both the outgoing and the incoming copy of a screen read and write the same
 * state and an open dialog simply carries over.
 *
 * Why not `rememberSaveable` + `SaveableStateHolder`: during the cross-fade
 * between the two layout modes BOTH branches are composed for a few frames, so
 * the same screen key would be registered twice and
 * `SaveableStateHolder.SaveableStateProvider` throws
 * "Key ... was used multiple times". Sharing one entry is safe under overlap.
 *
 * Scope: this is deliberately NOT persistence. Values live only as long as the
 * process, exactly like the `remember` they replace, and [forgetAllExcept]
 * drops a screen's entries when the user navigates away so that leaving a
 * screen still closes its dialogs.
 */
class RetainedStateStore {
    /** Keys are "<ScreenName>.<field>"; the prefix is what [forgetAllExcept] matches on. */
    private val entries = mutableMapOf<String, MutableState<*>>()

    @Suppress("UNCHECKED_CAST")
    fun <T> state(key: String, init: () -> T): MutableState<T> =
        entries.getOrPut(key) { mutableStateOf(init()) } as MutableState<T>

    /**
     * Drops every entry that does not belong to one of [keepPrefixes]. Called on
     * navigation (never on rotation, which does not change the current screen),
     * so a dialog left open on a screen the user walked away from does not
     * reappear when they come back.
     */
    fun forgetAllExcept(keepPrefixes: Collection<String>) {
        entries.keys.retainAll { key ->
            keepPrefixes.any { prefix -> key.startsWith("$prefix.") }
        }
    }
}

/**
 * Provided by `NazoApp` above the layout-mode switch. Null outside that scope,
 * in which case [rememberRetained] degrades to a plain `remember` — previews
 * and tests keep working.
 */
val LocalRetainedStateStore = compositionLocalOf<RetainedStateStore?> { null }

/**
 * Drop-in replacement for `remember { mutableStateOf(...) }` whose value
 * survives the orientation layout swap.
 *
 * @param key globally unique and prefixed with the owning screen, e.g.
 *   "BackupRestore.showBackupPreview", so navigation can prune it.
 */
@Composable
fun <T> rememberRetained(key: String, init: () -> T): MutableState<T> {
    val store = LocalRetainedStateStore.current ?: return remember { mutableStateOf(init()) }
    return remember(store, key) { store.state(key, init) }
}
