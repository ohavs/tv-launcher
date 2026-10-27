package com.ohav.tvhome.camera

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** A binding to [CameraService]; [close] unbinds, which releases its player. */
class CameraLink private constructor(private val context: Context, private val conn: ServiceConnection, val player: ICameraPlayer) {
    fun close() {
        runCatching { context.unbindService(conn) }
    }

    companion object {
        /** Binds to the camera process; [onDied] fires if that process goes away while bound. */
        suspend fun connect(context: Context, onDied: () -> Unit): CameraLink = suspendCancellableCoroutine { cont ->
            val conn = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                    if (!cont.isActive) return
                    val link = CameraLink(context, this, ICameraPlayer.Stub.asInterface(binder))
                    cont.resume(link) { _, l, _ -> l.close() }
                }

                override fun onServiceDisconnected(name: ComponentName) = onDied()
            }
            if (!context.bindService(Intent(context, CameraService::class.java), conn, Context.BIND_AUTO_CREATE)) {
                runCatching { context.unbindService(conn) }
                cont.cancel(IllegalStateException("camera service unavailable"))
                return@suspendCancellableCoroutine
            }
            cont.invokeOnCancellation { runCatching { context.unbindService(conn) } }
        }
    }
}
