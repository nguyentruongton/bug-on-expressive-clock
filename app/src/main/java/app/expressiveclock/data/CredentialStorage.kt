package app.expressiveclock.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.UserManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

internal fun Context.credentialStorageAvailable(): Boolean =
    getSystemService(UserManager::class.java)?.isUserUnlocked == true

/** Register before rechecking to cover unlocking between the initial check and registration. */
internal fun Context.credentialUnlocks(): Flow<Boolean> = callbackFlow {
    if (credentialStorageAvailable()) {
        trySend(true)
        close()
        return@callbackFlow
    }
    android.util.Log.i("ClockStorage", "Deferring credential-protected storage until USER_UNLOCKED")
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_UNLOCKED && credentialStorageAvailable()) trySend(true)
        }
    }
    ContextCompat.registerReceiver(this@credentialUnlocks, receiver,
        IntentFilter(Intent.ACTION_USER_UNLOCKED), ContextCompat.RECEIVER_NOT_EXPORTED)
    if (credentialStorageAvailable()) trySend(true)
    awaitClose { unregisterReceiver(receiver) }
}

/** The supplier must stay lazy: even creating/reading the delegate while locked can cache defaults. */
internal fun <T> afterCredentialUnlock(unlocked: Flow<Boolean>, data: () -> Flow<T>): Flow<T> = flow {
    unlocked.first { it }
    emitAll(data())
}
