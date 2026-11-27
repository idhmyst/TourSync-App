package putri.data

import android.content.Context
import putri.core.pickup.PickupPassService
import putri.data.repo.*
import putri.data.seed.DataSeeder

/**
 * Titik rakit semua repository. Contoh di Application:
 *   val container = PutriContainer(this, secret = BuildConfig.PASS_SECRET.toByteArray())
 *   lifecycleScope.launch { container.seeder.seedIfEmpty() }
 * (Catatan: kunci HMAC di app hanya untuk demo; produksi sebaiknya divalidasi server.)
 */
class PutriContainer(context: Context, secret: ByteArray, db: PutriDatabase = PutriDatabase.build(context), clock: Clock = { System.currentTimeMillis() }) {
    val database = db
    private val passes = PickupPassService(secret)

    val auth = AuthRepository(db, clock)
    val catalog = CatalogRepository(db)
    val cart = CartRepository(db, clock)
    val orders = OrderRepository(db, passes, clock)
    val wallet = WalletRepository(db, clock)
    val trips = TripRepository(db, clock)
    val budget = BudgetRepository(db, clock)
    val seeder = DataSeeder(db, auth)
}
