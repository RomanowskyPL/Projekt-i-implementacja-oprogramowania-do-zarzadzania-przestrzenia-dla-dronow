package pl.twoja.apka

import android.app.Application
import android.content.Context
import android.util.Log
import pl.twoja.apka.dji.DjiManager
import com.dji.wpmzsdk.manager.WPMZManager

class App : Application() {

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        com.cySdkyc.clx.Helper.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        DjiManager.init(this) { ok, msg ->
            Log.d("App", "DJI init: ok=$ok, msg=$msg")
        }
        WPMZManager.getInstance().init(this)
    }
}
