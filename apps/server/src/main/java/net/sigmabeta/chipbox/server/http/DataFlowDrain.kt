package net.sigmabeta.chipbox.server.http

import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import net.sigmabeta.chipbox.repository.Data

/**
 * Drain a `Flow<Data<T>>` to its first settled (non-Loading) emission. For HTTP routes that need
 * "give me the current value, then stop subscribing" — the Repository's Flow returns are
 * conceptually streams, but HTTP responses are one-shot. `Data.Failed` becomes an `IOException`
 * so the global `StatusPages` plugin can convert it to a 500.
 */
internal suspend fun <T> Flow<Data<List<T>>>.firstSettled(): List<T> =
    when (val d = first { it !is Data.Loading }) {
        is Data.Succeeded -> d.data
        is Data.Empty -> emptyList()
        is Data.Failed -> throw IOException(d.message)
        Data.Loading -> emptyList() // unreachable per the `first { … }` predicate
    }

/** Same shape for nullable-single Repository queries (`getGame(id)` etc.). */
internal suspend fun <T : Any> Flow<Data<T?>>.firstSettledSingle(): T? =
    when (val d = first { it !is Data.Loading }) {
        is Data.Succeeded -> d.data
        is Data.Empty -> null
        is Data.Failed -> throw IOException(d.message)
        Data.Loading -> null
    }
