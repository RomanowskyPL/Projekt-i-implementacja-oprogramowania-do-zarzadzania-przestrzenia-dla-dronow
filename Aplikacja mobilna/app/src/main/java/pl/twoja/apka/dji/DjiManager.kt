package pl.twoja.apka.dji

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import dji.v5.common.error.IDJIError
import dji.v5.common.register.DJISDKInitEvent
import dji.v5.manager.SDKManager
import dji.v5.manager.interfaces.SDKManagerCallback

object DjiManager {

    private const val TAG = "DjiManager"
    @Volatile private var initialized = false
    @Volatile private var registered = false
    @Volatile private var productConnected = false
    @Volatile private var initStarted = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastStatus: String = "DJI SDK: nie zainicjalizowano"
    val isReady: Boolean get() = initialized && registered && productConnected

    fun status(): String = lastStatus

    fun toastStatus(ctx: Context) {
        mainHandler.post { Toast.makeText(ctx, lastStatus, Toast.LENGTH_LONG).show() }
    }

    fun init(app: Application, onUpdate: (ok: Boolean, msg: String) -> Unit) {

        if (isReady) {
            onUpdate(true, "DJI SDK: gotowe")
            return
        }
        if (initStarted) {
            onUpdate(false, lastStatus)
            return
        }
        initStarted = true
        lastStatus = "DJI SDK: init..."
        Log.i(TAG, lastStatus)
        onUpdate(false, lastStatus)
        SDKManager.getInstance().init(app, object : SDKManagerCallback {
            override fun onInitProcess(event: DJISDKInitEvent, totalProcess: Int) {
                Log.i(TAG, "onInitProcess: event=$event total=$totalProcess")
                if (event == DJISDKInitEvent.INITIALIZE_COMPLETE) {
                    initialized = true
                    lastStatus = "DJI SDK: init OK, rejestruję..."
                    Log.i(TAG, lastStatus)
                    onUpdate(false, lastStatus)
                    runCatching { SDKManager.getInstance().registerApp() }
                        .onFailure { t ->
                            lastStatus = "DJI SDK: registerApp wyjątek: ${t.message}"
                            Log.e(TAG, lastStatus, t)
                            initStarted = false
                            onUpdate(false, lastStatus)
                        }
                }
            }

            override fun onRegisterSuccess() {
                registered = true
                lastStatus = "DJI SDK: rejestracja OK, czekam na połączenie z dronem..."
                Log.i(TAG, lastStatus)
                onUpdate(false, lastStatus)
            }

            override fun onRegisterFailure(error: IDJIError) {
                registered = false
                lastStatus = "DJI SDK: rejestracja FAIL: ${error.description()}"
                Log.e(TAG, lastStatus)
                initStarted = false
                onUpdate(false, lastStatus)
            }

            override fun onProductConnect(productId: Int) {
                productConnected = true
                lastStatus = "DJI: product CONNECT (id=$productId) — gotowe"
                Log.i(TAG, lastStatus)
                onUpdate(true, lastStatus)
            }

            override fun onProductDisconnect(productId: Int) {
                productConnected = false
                lastStatus = "DJI: product DISCONNECT (id=$productId)"
                Log.w(TAG, lastStatus)
                onUpdate(false, lastStatus)
            }

            override fun onProductChanged(productId: Int) {
                lastStatus = "DJI: product CHANGED (id=$productId)"
                Log.i(TAG, lastStatus)
                onUpdate(false, lastStatus)
            }

            override fun onDatabaseDownloadProgress(current: Long, total: Long) {}
        })
    }
}
