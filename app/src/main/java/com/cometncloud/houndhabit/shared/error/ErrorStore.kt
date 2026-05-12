package com.cometncloud.houndhabit.shared.error

import android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Process-wide error channel. Collected once by [com.cometncloud.houndhabit.AppRouter]
 * and surfaced via a snackbar above whatever screen is current.
 *
 * Use this for errors that originate **outside** a screen — background workers,
 * service calls made from lifecycle hooks, RPC failures in singletons. Errors
 * raised inside a ViewModel that already drives a per-screen snackbar should
 * stay where they are; rerouting them through here would make the user lose
 * the screen-local context (e.g. which item failed to save).
 *
 * Safe to call from any thread / dispatcher. Capped buffer drops the oldest
 * message under sustained pressure so a flood from one source doesn't OOM.
 */
object ErrorStore {
    private const val TAG = "ErrorStore"
    private val channel = Channel<String>(capacity = 8, onBufferOverflow =
        kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)

    val messages: Flow<String> = channel.receiveAsFlow()

    fun emit(message: String) {
        Log.d(TAG, message)
        channel.trySend(message)
    }

    /** Convenience for `Throwable` callers — picks a usable message. */
    fun emit(t: Throwable, fallback: String = "Something went wrong.") {
        emit(t.message?.takeIf { it.isNotBlank() } ?: fallback)
    }
}
