package com.acceleratorer.wuwavn

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject
import rikka.shizuku.Shizuku

class WuWa36PreflightReader {
    fun read(context: Context): WuWa36Snapshot? {
        val serviceRef = AtomicReference<IWuwaPatchService?>()
        val connected = CountDownLatch(1)
        val componentName = ComponentName(context, WuwaPatchUserService::class.java)
        val args = Shizuku.UserServiceArgs(componentName)
            .daemon(false)
            .debuggable(false)
            .processNameSuffix("preflight36")
            .tag("preflight36")
            .version(AppConstants.VERSION_CODE)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                serviceRef.set(IWuwaPatchService.Stub.asInterface(service))
                connected.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName) {
                serviceRef.set(null)
            }
        }
        try {
            Shizuku.bindUserService(args, connection)
            if (!connected.await(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw IllegalStateException("Timed out reading WUWA 3.6 Resources layout.")
            }
            val service = serviceRef.get()
                ?: throw IllegalStateException("WUWA 3.6 preflight service did not connect.")
            val json = service.wuwa36Snapshot(AppConstants.SUPPORTED_GAME_VERSION)
            return if (JSONObject(json).optBoolean("ready")) WuWa36Snapshot.fromJson(json) else null
        } finally {
            runCatching { Shizuku.unbindUserService(args, connection, true) }
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_SECONDS = 10L
    }
}
