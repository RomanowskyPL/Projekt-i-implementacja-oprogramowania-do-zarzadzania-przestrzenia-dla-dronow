package pl.twoja.apka

import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.ComponentActivity
import java.net.URL
import kotlin.concurrent.thread

class PhotoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo)
        val url = intent.getStringExtra("url")
        val iv = findViewById<ImageView>(R.id.img)

        if (!url.isNullOrBlank()) {
            thread {
                runCatching {
                    val stream = URL(url).openStream()
                    val bmp = BitmapFactory.decodeStream(stream)
                    runOnUiThread { iv.setImageBitmap(bmp) }
                }
            }
        }
    }
}
