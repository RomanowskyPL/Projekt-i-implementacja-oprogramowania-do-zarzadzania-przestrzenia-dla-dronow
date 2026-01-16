package pl.twoja.apka

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat


class MainActivity : ComponentActivity() {
    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val denied = result.filterValues { granted -> !granted }.keys
        if (denied.isNotEmpty()) {
            Toast.makeText(
                this,
                "Nie przyznano uprawnień: ${denied.joinToString()}.\nDJI/Bluetooth mogą działać gorzej.",
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(this, "Uprawnienia przyznane ✅", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        val content = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        requestRuntimePermissionsIfNeeded()

        findViewById<Button>(R.id.btnOperator).setOnClickListener {
            startActivity(Intent(this, OperatorActivity::class.java))
        }
        findViewById<View>(R.id.btnDrony).setOnClickListener {
            startActivity(Intent(this, DronesActivity::class.java))
        }
        findViewById<View>(R.id.btnRoutes).setOnClickListener {
            startActivity(Intent(this, RoutesActivity::class.java))
        }
        findViewById<View>(R.id.btnFlight).setOnClickListener {
            startActivity(Intent(this, FlightsActivity::class.java))
        }
    }

    private fun requestRuntimePermissionsIfNeeded() {
        val needed = requiredRuntimePermissions().filter { perm ->
            ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            permLauncher.launch(needed.toTypedArray())
        }
    }

    private fun requiredRuntimePermissions(): List<String> {
        val perms = mutableListOf<String>()
        perms += Manifest.permission.ACCESS_FINE_LOCATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms += Manifest.permission.BLUETOOTH_SCAN
            perms += Manifest.permission.BLUETOOTH_CONNECT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms += Manifest.permission.POST_NOTIFICATIONS
        }
        return perms
    }
}
