/*
 * LoadErrors.kt (Suvir)
 *
 * What happens when live data can't be loaded (B15): the read is tried again a few times, then the
 * screen keeps going with what it has, and a banner says something couldn't be loaded, with
 * Try again, instead of an empty list quietly passing for "nothing here".
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retryWhen

/** Live reads that failed after their retries, and the Try again that starts them over. */
object LoadErrors {
    /** Waits before each retry: 1 s, 2 s, then 4 s. */
    val RETRY_DELAYS_MS = listOf(1_000L, 2_000L, 4_000L)

    private val _problem = MutableStateFlow<String?>(null)
    private val _attempt = MutableStateFlow(0)

    /** What to tell the person when a read failed after its retries, or null when all is well. */
    val problem: StateFlow<String?> = _problem.asStateFlow()

    /** Goes up by one on each Try again; live reads start over when it changes. */
    val attempt: StateFlow<Int> = _attempt.asStateFlow()

    /** Records that a read failed for good, with [message] to show. */
    fun report(message: String) { _problem.value = message }

    /** Try again: clears the banner and starts every live read over. */
    fun tryAgain() {
        _problem.value = null
        _attempt.value += 1
    }

    /** Forgets any problem (between tests). */
    fun reset() { _problem.value = null }
}

/**
 * Retries this live read after [delays] when it fails, then (if it still fails) reports it to
 * [LoadErrors] with [describe]'s message and carries on with [fallback], so screens still draw.
 */
fun <T> Flow<T>.recoverLoad(
    fallback: T,
    delays: List<Long> = LoadErrors.RETRY_DELAYS_MS,
    describe: (Throwable) -> String = { "Couldn't load everything. Check your connection and try again." },
): Flow<T> = retryWhen { _, attempt ->
    if (attempt < delays.size) { delay(delays[attempt.toInt()]); true } else false
}.catch { e ->
    LoadErrors.report(describe(e))
    emit(fallback)
}
