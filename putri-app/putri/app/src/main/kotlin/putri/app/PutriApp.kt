package putri.app

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import putri.data.PutriContainer

class PutriApp : Application() {
    lateinit var container: PutriContainer
    lateinit var session: Session
    val ready = MutableStateFlow(false)

    override fun onCreate() {
        super.onCreate()
        // Kunci HMAC QR hanya untuk demo; produksi: validasi di server.
        container = PutriContainer(this, "putri-demo-secret-change-me".toByteArray())
        session = Session(getSharedPreferences("putri", MODE_PRIVATE))
        CoroutineScope(Dispatchers.Default).launch {
            container.seeder.seedIfEmpty()
            ready.value = true
        }
    }
}
