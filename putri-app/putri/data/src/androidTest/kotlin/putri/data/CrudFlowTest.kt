package putri.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import putri.core.budget.BudgetRequest
import putri.core.budget.Duration
import putri.core.budget.TravelStyle
import putri.core.pickup.PassStatus
import putri.core.splitbill.ExpenseCategory
import putri.data.entity.*
import putri.data.repo.ExpenseInput
import kotlin.test.*

@RunWith(AndroidJUnit4::class)
class CrudFlowTest {
    private lateinit var db: PutriDatabase
    private lateinit var c: PutriContainer
    private var now = 1_700_000_000_000L

    @Before fun setup() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = PutriDatabase.inMemory(ctx)
        c = PutriContainer(ctx, "test-secret".toByteArray(), db) { now }
        c.seeder.seedIfEmpty()
    }

    @After fun tearDown() { db.close() }

    private suspend fun login(u: String) = c.auth.login(u, "password123").getOrThrow()
    private suspend fun product(part: String) = db.productDao().inStock().first { it.name.contains(part) }
    private suspend fun stationHub() = c.catalog.hubs().first().first { it.name.contains("Stasiun") }

    // ---------------- akun ----------------
    @Test fun authRules() = runBlocking {
        assertTrue(c.auth.register(UserRole.WISATAWAN, "Rian Dua", "rian", "x@y.id", "081200000009", "password123").isFailure) // username dobel
        assertTrue(c.auth.register(UserRole.WISATAWAN, "A B", "abcd", "x@y.id", "081200000009", "pendek").isFailure)
        assertTrue(c.auth.login("rian", "salah-banget").isFailure)
        assertEquals("rian", c.auth.login("rian@baturraden.id", "password123").getOrThrow().username) // login pakai email
        assertEquals("#BMS-9001", login("rian").publicCode)
    }

    @Test fun onlyOwnerManagesProducts() = runBlocking {
        val rian = login("rian"); val nanik = login("bunanik")
        val partner = c.catalog.partnersOf(nanik.id).first()
        val p = ProductEntity(partnerId = partner.id, name = "Gethuk Baru", category = "Getuk Sokaraja", price = 15_000, stock = 5)
        assertTrue(c.catalog.addProduct(rian.id, p).isFailure)       // wisatawan ditolak
        val id = c.catalog.addProduct(nanik.id, p).getOrThrow()      // pemilik boleh
        assertTrue(c.catalog.setStock(nanik.id, id, -1).isFailure)
        c.catalog.setStock(nanik.id, id, 12).getOrThrow()
        assertEquals(12, c.catalog.product(id)!!.stock)
        c.catalog.deleteProduct(nanik.id, id).getOrThrow()
        assertNull(c.catalog.product(id))
    }

    // ---------------- oleh-oleh: keranjang -> bayar -> pickup ----------------
    @Test fun fullPickupFlow() = runBlocking {
        val rian = login("rian"); val mitra = login("bunanik"); val officer = login("hubpwt")
        val mendoan = product("Mendoan"); val getuk = product("Getuk")
        c.cart.add(rian.id, mendoan.id, 2).getOrThrow()
        c.cart.add(rian.id, getuk.id, 1).getOrThrow()
        assertTrue(c.cart.add(rian.id, mendoan.id, 999).isFailure)   // melebihi stok

        val hub = stationHub()
        val order = c.orders.createFromCart(rian.id, hub.id, now + 6 * 3_600_000L).getOrThrow()
        assertEquals(110_000L, order.subtotal)
        assertEquals(mendoan.stock - 2, c.catalog.product(mendoan.id)!!.stock)  // stok dipesan
        assertTrue(c.cart.observe(rian.id).first().rows.isEmpty())

        val paid = c.orders.pay(order.id, PaymentMethod.ECO_WALLET, usePoints = true).getOrThrow()
        assertEquals(97_000L, paid.total)        // 312 poin = Rp13.000
        val u = db.userDao().byId(rian.id)!!
        assertEquals(353_000L, u.walletBalance)
        assertEquals(17, u.ecoPoints)            // 320 - 312 + 9

        val pass = c.orders.markReadyForPickup(mitra.id, order.id, "LOKER-08").getOrThrow()
        assertEquals(LockerStatus.OCCUPIED, db.hubDao().lockerByCode("LOKER-08")!!.status)
        assertEquals(OrderStatus.READY_PICKUP, db.orderDao().byId(order.id)!!.status)

        assertEquals(PassStatus.WRONG_LOCKER, c.orders.redeem(officer.id, pass.token, "LOKER-07").status)
        assertEquals(PassStatus.INVALID, c.orders.redeem(rian.id, pass.token, "LOKER-08").status)   // bukan petugas
        assertEquals(PassStatus.READY, c.orders.redeem(officer.id, pass.backupCode, "LOKER-08").status) // kode manual
        assertEquals(PassStatus.USED, c.orders.redeem(officer.id, pass.token, "LOKER-08").status)
        assertEquals(OrderStatus.COMPLETED, db.orderDao().byId(order.id)!!.status)
        assertEquals(LockerStatus.FREE, db.hubDao().lockerByCode("LOKER-08")!!.status)
    }

    @Test fun cancelAndExpireRestoreStock() = runBlocking {
        val rian = login("rian"); val hub = stationHub(); val m = product("Mendoan")
        c.cart.add(rian.id, m.id, 3).getOrThrow()
        val o1 = c.orders.createFromCart(rian.id, hub.id, now + 3_600_000L).getOrThrow()
        c.orders.cancel(o1.id).getOrThrow()
        assertEquals(m.stock, c.catalog.product(m.id)!!.stock)

        c.cart.add(rian.id, m.id, 2).getOrThrow()
        val o2 = c.orders.createFromCart(rian.id, hub.id, now + 3_600_000L).getOrThrow()
        now += 16 * 60_000L                                       // lewat jendela bayar 15 menit
        assertEquals(1, c.orders.expireOverdue())
        assertEquals(OrderStatus.EXPIRED, db.orderDao().byId(o2.id)!!.status)
        assertEquals(m.stock, c.catalog.product(m.id)!!.stock)
        assertTrue(c.orders.pay(o2.id, PaymentMethod.QRIS).isFailure)
    }

    @Test fun cancelPaidOrderRefundsWallet() = runBlocking {
        val rian = login("rian"); val hub = stationHub()
        c.cart.add(rian.id, product("Keripik").id, 2).getOrThrow()
        val o = c.orders.createFromCart(rian.id, hub.id, now + 3_600_000L).getOrThrow()
        c.orders.pay(o.id, PaymentMethod.ECO_WALLET).getOrThrow()
        assertEquals(400_000L, db.userDao().byId(rian.id)!!.walletBalance)
        c.orders.cancel(o.id).getOrThrow()
        assertEquals(450_000L, db.userDao().byId(rian.id)!!.walletBalance)
    }

    @Test fun ticketOrderGetsGatePass() = runBlocking {
        val rian = login("rian"); val officer = login("hubpwt")
        val dest = c.catalog.searchDestinations("Limpakuwus").first()
        val t = c.catalog.tickets(dest.id).first().first()
        val o = c.orders.createTicketOrder(rian.id, t.id, 2, now + 8 * 3_600_000L).getOrThrow()
        val paid = c.orders.pay(o.id, PaymentMethod.QRIS).getOrThrow()
        assertEquals(OrderStatus.READY_PICKUP, paid.status)
        val pass = c.orders.detail(o.id)!!.pass!!
        assertEquals(PassKind.GATE, pass.kind)
        assertEquals(PassStatus.READY, c.orders.redeem(officer.id, pass.token, "GATE-${dest.id}").status)
    }

    // ---------------- grup trip & split-bill ----------------
    @Test fun tripSplitBillAndSettle() = runBlocking {
        val rian = login("rian")
        val names = listOf("dimas" to "Dimas Prasetyo", "sarah" to "Sarah Almira", "nabila" to "Nabila Putri")
        val ids = names.mapIndexed { i, (u, n) ->
            c.auth.register(UserRole.WISATAWAN, n, u, "$u@mail.id", "08130000010$i", "password123").getOrThrow().id
        }
        val (dimas, sarah, nabila) = ids

        val trip = c.trips.createTrip(rian.id, "Trip Baturraden Squad").getOrThrow()
        names.forEach { c.trips.invite(rian.id, trip.id, it.first).getOrThrow() }
        assertTrue(c.trips.invite(dimas, trip.id, "sarah").isFailure)       // bukan admin

        c.trips.contribute(rian.id, trip.id, 400_000).getOrThrow()
        assertTrue(c.trips.contribute(sarah, trip.id, 1_000).isFailure)     // saldo sarah 0

        c.trips.addExpense(rian.id, trip.id, ExpenseInput("Tiket Hutan Pinus", ExpenseCategory.TIKET, 60_000, rian.id)).getOrThrow()
        c.trips.addExpense(dimas, trip.id, ExpenseInput("Sewa Jeep Tour", ExpenseCategory.TRANSPORT, 450_000, dimas)).getOrThrow()
        c.trips.addExpense(rian.id, trip.id, ExpenseInput("Makan Siang", ExpenseCategory.KULINER, 180_000, null)).getOrThrow() // dari kas

        // validasi
        assertTrue(c.trips.addExpense(rian.id, trip.id, ExpenseInput("Salah", ExpenseCategory.LOGISTIK, 100_000, dimas,
            SplitMode.CUSTOM, customShares = mapOf(rian.id to 50_000L, dimas to 40_000L))).isFailure)
        assertTrue(c.trips.addExpense(rian.id, trip.id, ExpenseInput("Kebanyakan", ExpenseCategory.LOGISTIK, 300_000, null)).isFailure)
        assertEquals(180_000L, db.tripDao().poolSpent(trip.id))             // gagal -> tidak ada yang berubah

        val bal = c.trips.balances(trip.id)
        assertEquals(0L, bal.values.sum())
        assertEquals(322_500L, bal.getValue(dimas))
        val pending = db.tripDao().settlements(trip.id)
        assertEquals(3, pending.size)
        assertTrue(pending.all { it.toUserId == dimas })
        assertEquals(322_500L, pending.sumOf { it.amount })

        // sarah melunasi lewat wallet
        val s = pending.first { it.fromUserId == sarah }
        assertTrue(c.trips.payViaWallet(sarah, s.id).isFailure)             // saldo kurang
        c.wallet.topUp(sarah, 200_000).getOrThrow()
        c.trips.payViaWallet(sarah, s.id).getOrThrow()
        assertEquals(0L, c.trips.balances(trip.id).getValue(sarah))

        // nabila: tandai bayar -> dimas konfirmasi
        val sn = db.tripDao().settlements(trip.id).first { it.fromUserId == nabila && it.status == SettleStatus.BELUM_LUNAS }
        c.trips.markPaid(nabila, sn.id).getOrThrow()
        assertTrue(c.trips.confirm(nabila, sn.id).isFailure)                // hanya penerima
        c.trips.confirm(dimas, sn.id).getOrThrow()
        assertEquals(SettleStatus.LUNAS, db.tripDao().settlement(sn.id)!!.status)

        // anggota yang masih terlibat tidak bisa dikeluarkan; trip belum bisa diarsipkan
        assertTrue(c.trips.removeMember(rian.id, trip.id, sarah).isFailure)
        assertTrue(c.trips.archive(rian.id, trip.id).isFailure)
    }

    @Test fun payOrderFromGroupPool() = runBlocking {
        val rian = login("rian"); val hub = stationHub()
        val trip = c.trips.createTrip(rian.id, "Squad").getOrThrow()
        c.trips.contribute(rian.id, trip.id, 200_000).getOrThrow()
        c.cart.add(rian.id, product("Getuk").id, 2).getOrThrow()          // Rp80.000
        val o = c.orders.createFromCart(rian.id, hub.id, now + 3_600_000L).getOrThrow()
        assertTrue(c.orders.pay(o.id, PaymentMethod.GROUP_POOL).isFailure) // tanpa tripId
        c.orders.pay(o.id, PaymentMethod.GROUP_POOL, tripId = trip.id).getOrThrow()
        assertEquals(80_000L, db.tripDao().poolSpent(trip.id))
        c.orders.cancel(o.id).getOrThrow()                                  // batal -> kas pulih
        assertEquals(0L, db.tripDao().poolSpent(trip.id))
    }

    // ---------------- smart budget ----------------
    @Test fun budgetPlanRespectsAllergyAndBudget() = runBlocking {
        val rian = login("rian")
        c.auth.updateProfile(rian.id, rian.fullName, rian.email, rian.phone, "kacang").getOrThrow()
        val req = BudgetRequest(500_000, 2, Duration.FULL_DAY, TravelStyle.ALAM_RELAKSASI)
        val g = c.budget.generate(rian.id, req).getOrThrow()
        assertTrue(g.plan.totalCost <= 500_000)
        assertTrue(g.plan.umkmCost >= 50_000)
        assertTrue(g.plan.souvenirs.none { it.name.contains("Kacang") })
        assertEquals(1, c.budget.history(rian.id).first().size)
    }
}
