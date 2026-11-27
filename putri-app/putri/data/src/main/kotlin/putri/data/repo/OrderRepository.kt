package putri.data.repo

import androidx.room.withTransaction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import putri.core.payment.EcoPoints
import putri.core.payment.PaymentCalculator
import putri.core.pickup.PassStatus
import putri.core.pickup.PickupPassService
import putri.core.splitbill.ExpenseCategory
import putri.core.splitbill.SplitBill
import putri.data.PutriDatabase
import putri.data.entity.*

data class OrderDetail(
    val order: OrderEntity,
    val items: List<OrderItemEntity>,
    val payment: PaymentEntity?,
    val pass: PickupPassEntity?
)

data class RedeemResult(val status: PassStatus, val order: OrderEntity? = null)

/**
 * Siklus pesanan:
 *  keranjang/tiket -> WAITING_PAYMENT (15 menit) -> pay() -> PAID
 *  -> Mitra markReadyForPickup() -> READY_PICKUP (QR terbit) -> Petugas redeem() -> COMPLETED
 *  Tiket wisata: setelah dibayar langsung READY_PICKUP dengan QR gerbang.
 */
class OrderRepository(
    private val db: PutriDatabase,
    private val passes: PickupPassService,
    private val clock: Clock = systemClock
) {
    private val orders = db.orderDao()
    private val users = db.userDao()
    private val products = db.productDao()
    private val cart = db.cartDao()
    private val hubs = db.hubDao()
    private val trips = db.tripDao()
    private val wallet = db.walletDao()
    private val catalog = db.catalogDao()

    companion object { const val PAYMENT_WINDOW_MS = 15 * 60 * 1000L }

    // ---------- baca ----------
    fun myOrders(userId: Long): Flow<List<OrderEntity>> = orders.observeByUser(userId)
    fun myOrders(userId: Long, status: OrderStatus) = orders.observeByUserAndStatus(userId, status)
    fun queueForPartner(ownerUserId: Long, status: OrderStatus = OrderStatus.PAID) = orders.observeForPartner(ownerUserId, status)

    suspend fun detail(orderId: Long): OrderDetail? {
        val o = orders.byId(orderId) ?: return null
        return OrderDetail(o, orders.items(orderId), orders.paymentOf(orderId), orders.passOf(orderId))
    }

    // ---------- buat pesanan ----------
    suspend fun createFromCart(userId: Long, hubId: Long, pickupDeadline: Long): Result<OrderEntity> = runCatching {
        db.withTransaction {
            val rows = cart.rows(userId)
            require(rows.isNotEmpty()) { "Keranjang kosong" }
            val hub = hubs.hub(hubId)
            require(hub != null && hub.active) { "Titik pickup tidak tersedia" }
            require(pickupDeadline > clock()) { "Batas pengambilan sudah lewat" }
            val id = newOrder(userId, OrderKind.OLEH_OLEH, rows.sumOf { it.price * it.qty }, hubId, pickupDeadline)
            rows.forEach { r ->
                require(products.reserve(r.productId, r.qty) == 1) { "Stok '${r.name}' tidak mencukupi" }
                orders.insertItem(OrderItemEntity(orderId = id, productId = r.productId, name = r.name, subtitle = r.partnerName, unitPrice = r.price, qty = r.qty))
            }
            cart.clear(userId)
            orders.byId(id)!!
        }
    }

    /** Pesan tiket destinasi ("Pesan Pass"). validUntil = batas berlaku QR gerbang. */
    suspend fun createTicketOrder(userId: Long, ticketPackageId: Long, qty: Int, validUntil: Long): Result<OrderEntity> = runCatching {
        require(qty in 1..50) { "Jumlah tiket 1-50" }
        require(validUntil > clock()) { "Tanggal kunjungan sudah lewat" }
        db.withTransaction {
            val t = catalog.ticket(ticketPackageId)
            require(t != null && t.active) { "Paket tiket tidak tersedia" }
            val dest = requireNotNull(catalog.destination(t.destinationId)) { "Destinasi tidak ditemukan" }
            require(dest.active) { "Destinasi sedang tidak aktif" }
            val id = newOrder(userId, OrderKind.TIKET, t.price * qty, null, validUntil)
            orders.insertItem(OrderItemEntity(orderId = id, ticketPackageId = t.id, name = t.name, subtitle = dest.name, unitPrice = t.price, qty = qty))
            orders.byId(id)!!
        }
    }

    // ---------- bayar ----------
    suspend fun pay(
        orderId: Long, method: PaymentMethod, usePoints: Boolean = false, tripId: Long? = null
    ): Result<OrderEntity> = runCatching {
        db.withTransaction {
            val order = requireNotNull(orders.byId(orderId)) { "Pesanan tidak ditemukan" }
            require(order.status == OrderStatus.WAITING_PAYMENT) { "Pesanan tidak sedang menunggu pembayaran" }
            val now = clock()
            require(now <= order.paymentExpiresAt) { "Waktu pembayaran habis" }
            val user = requireNotNull(users.byId(order.userId)) { "Akun tidak ditemukan" }

            val co = PaymentCalculator.checkout(order.subtotal, user.ecoPoints, usePoints)
            when (method) {
                PaymentMethod.ECO_WALLET -> {
                    require(users.debit(user.id, co.total) == 1) { "Saldo Eco-Wallet tidak cukup" }
                    wallet.insertTx(WalletTxEntity(userId = user.id, type = WalletTxType.PAYMENT, amount = co.total,
                        balanceAfter = users.byId(user.id)!!.walletBalance, ref = order.code, createdAt = now))
                }
                PaymentMethod.GROUP_POOL -> payFromPool(order, user.id, tripId, co.total, now)
                else -> Unit // QRIS / VA: gateway disimulasikan, dianggap sukses
            }
            if (co.pointsUsed > 0) {
                require(users.addPoints(user.id, -co.pointsUsed) == 1) { "Poin tidak cukup" }
                wallet.insertPoints(EcoPointTxEntity(userId = user.id, delta = -co.pointsUsed, reason = "Tukar poin ${order.code}", createdAt = now))
            }
            if (co.pointsEarned > 0) {
                users.addPoints(user.id, co.pointsEarned)
                wallet.insertPoints(EcoPointTxEntity(userId = user.id, delta = co.pointsEarned, reason = "Belanja ${order.code}", createdAt = now))
            }
            orders.insertPayment(PaymentEntity(orderId = order.id, method = method, amount = co.total,
                reference = "PAY-${order.trxCode}", createdAt = now))

            var updated = order.copy(status = OrderStatus.PAID, pointsUsed = co.pointsUsed, discount = co.discount,
                total = co.total, paidAt = now)
            if (order.kind == OrderKind.TIKET) {
                val ticketId = orders.items(order.id).first().ticketPackageId!!
                val gate = "GATE-${catalog.ticket(ticketId)!!.destinationId}"
                val p = passes.issue(order.code, gate, order.pickupDeadline!!)
                orders.insertPass(PickupPassEntity(orderId = order.id, kind = PassKind.GATE, lockerCode = gate,
                    token = p.token, backupCode = p.backupCode, expiresAt = p.expiresAt))
                updated = updated.copy(status = OrderStatus.READY_PICKUP)
            }
            orders.update(updated)
            updated
        }
    }

    private suspend fun payFromPool(order: OrderEntity, userId: Long, tripId: Long?, total: Long, now: Long) {
        require(tripId != null) { "Pilih grup untuk memakai Dompet Bersama" }
        require(trips.member(tripId, userId) != null) { "Anda bukan anggota grup ini" }
        require(trips.trip(tripId)?.status == TripStatus.ACTIVE) { "Grup sudah diarsipkan" }
        require(total <= trips.collected(tripId) - trips.poolSpent(tripId)) { "Saldo kas bersama tidak cukup" }
        if (total == 0L) return
        val memberIds = trips.memberIds(tripId)
        val expId = trips.insertExpense(GroupExpenseEntity(
            tripId = tripId, title = "Pesanan ${order.code}",
            category = if (order.kind == OrderKind.TIKET) ExpenseCategory.TIKET else ExpenseCategory.LOGISTIK,
            amount = total, paidByUserId = null, splitMode = SplitMode.EQUAL,
            paymentMethod = "Dompet Bersama", orderId = order.id, createdAt = now
        ))
        trips.insertShares(SplitBill.splitEqually(total, memberIds.map { it.toString() })
            .map { (k, v) -> ExpenseShareEntity(expId, k.toLong(), v) })
    }

    // ---------- batal / kedaluwarsa ----------
    suspend fun cancel(orderId: Long): Result<OrderEntity> = runCatching {
        db.withTransaction {
            val o = requireNotNull(orders.byId(orderId)) { "Pesanan tidak ditemukan" }
            require(o.status == OrderStatus.WAITING_PAYMENT || o.status == OrderStatus.PAID) { "Pesanan tidak bisa dibatalkan" }
            restock(o)
            if (o.status == OrderStatus.PAID) refund(o)
            orders.update(o.copy(status = OrderStatus.CANCELLED))
            orders.byId(orderId)!!
        }
    }

    /**
     * Rapikan pesanan lewat waktu: belum dibayar > 15 menit -> EXPIRED (stok dikembalikan);
     * lewat batas ambil -> EXPIRED (QR hangus, loker dibebaskan, tanpa refund otomatis).
     * Panggil saat app dibuka / lewat WorkManager. Mengembalikan jumlah pesanan yang diubah.
     */
    suspend fun expireOverdue(): Int = db.withTransaction {
        val now = clock()
        var n = 0
        orders.overduePayments(now).forEach { o ->
            restock(o); orders.update(o.copy(status = OrderStatus.EXPIRED)); n++
        }
        orders.overduePickups(now).forEach { o ->
            orders.passOf(o.id)?.let { p ->
                orders.updatePass(p.copy(state = PassState.EXPIRED))
                if (p.kind == PassKind.LOCKER) hubs.setLockerStatus(p.lockerCode, LockerStatus.FREE)
            }
            orders.update(o.copy(status = OrderStatus.EXPIRED)); n++
        }
        n
    }

    private suspend fun restock(o: OrderEntity) {
        orders.items(o.id).forEach { i -> i.productId?.let { products.release(it, i.qty) } }
    }

    private suspend fun refund(o: OrderEntity) {
        val now = clock()
        val pay = orders.paymentOf(o.id)
        if (pay?.method == PaymentMethod.GROUP_POOL) {
            trips.deleteExpenseByOrder(o.id) // kas bersama pulih otomatis
        } else if (o.total > 0) {
            users.credit(o.userId, o.total)
            wallet.insertTx(WalletTxEntity(userId = o.userId, type = WalletTxType.REFUND, amount = o.total,
                balanceAfter = users.byId(o.userId)!!.walletBalance, ref = o.code, createdAt = now))
        }
        pay?.let { orders.updatePayment(it.copy(status = PaymentStatus.REFUNDED)) }
        if (o.pointsUsed > 0) {
            users.addPoints(o.userId, o.pointsUsed)
            wallet.insertPoints(EcoPointTxEntity(userId = o.userId, delta = o.pointsUsed, reason = "Refund poin ${o.code}", createdAt = now))
        }
        val earned = EcoPoints.earned(o.total)
        if (earned > 0 && users.addPoints(o.userId, -earned) == 1) {
            wallet.insertPoints(EcoPointTxEntity(userId = o.userId, delta = -earned, reason = "Pembatalan ${o.code}", createdAt = now))
        }
        orders.passOf(o.id)?.let { orders.updatePass(it.copy(state = PassState.EXPIRED)) }
    }

    // ---------- Mitra UMKM: siapkan pesanan ----------
    suspend fun markReadyForPickup(partnerUserId: Long, orderId: Long, lockerCode: String): Result<PickupPassEntity> = runCatching {
        db.withTransaction {
            val actor = users.byId(partnerUserId)
            require(actor?.role == UserRole.MITRA_UMKM) { "Khusus Mitra UMKM" }
            val o = requireNotNull(orders.byId(orderId)) { "Pesanan tidak ditemukan" }
            require(o.kind == OrderKind.OLEH_OLEH && o.status == OrderStatus.PAID) { "Pesanan belum siap diproses" }
            val mine = orders.items(orderId).any { i ->
                i.productId?.let { pid -> products.byId(pid)?.let { p -> products.partner(p.partnerId)?.ownerUserId == partnerUserId } } == true
            }
            require(mine) { "Pesanan ini tidak memuat produk Anda" }
            val locker = requireNotNull(hubs.lockerByCode(lockerCode)) { "Loker tidak ditemukan" }
            require(locker.hubId == o.hubId) { "Loker bukan di titik pickup pesanan" }
            require(locker.status == LockerStatus.FREE) { "Loker tidak kosong" }
            val p = passes.issue(o.code, locker.code, o.pickupDeadline!!)
            val entity = PickupPassEntity(orderId = o.id, kind = PassKind.LOCKER, lockerCode = locker.code,
                token = p.token, backupCode = p.backupCode, expiresAt = p.expiresAt)
            orders.insertPass(entity)
            hubs.setLockerStatus(locker.code, LockerStatus.OCCUPIED)
            orders.update(o.copy(status = OrderStatus.READY_PICKUP))
            entity
        }
    }

    // ---------- Petugas Hub / gerbang: scan QR ----------
    /** tokenOrBackup boleh isi QR atau kode cadangan manual (BTR-XXXX-XXXX). */
    suspend fun redeem(officerUserId: Long, tokenOrBackup: String, scannedCode: String): RedeemResult = db.withTransaction {
        if (users.byId(officerUserId)?.role != UserRole.PETUGAS_HUB) return@withTransaction RedeemResult(PassStatus.INVALID)
        val input = tokenOrBackup.trim()
        val pass = (if (input.startsWith("BTR-")) orders.passByBackup(input.uppercase()) else orders.passByToken(input))
            ?: return@withTransaction RedeemResult(PassStatus.INVALID)
        if (pass.state == PassState.USED) return@withTransaction RedeemResult(PassStatus.USED)
        if (pass.state == PassState.EXPIRED) return@withTransaction RedeemResult(PassStatus.EXPIRED)
        val now = clock()
        val st = passes.validate(pass.token, scannedCode, now)
        if (st != PassStatus.READY) return@withTransaction RedeemResult(st)
        orders.updatePass(pass.copy(state = PassState.USED, redeemedAt = now))
        if (pass.kind == PassKind.LOCKER) hubs.setLockerStatus(pass.lockerCode, LockerStatus.FREE)
        val o = orders.byId(pass.orderId)!!
        orders.update(o.copy(status = OrderStatus.COMPLETED, completedAt = now))
        RedeemResult(PassStatus.READY, orders.byId(o.id))
    }

    /** Sisa waktu (ms) sebelum QR hangus, untuk countdown di E-Tiket. */
    suspend fun remainingMillis(orderId: Long): Long {
        val p = orders.passOf(orderId) ?: return 0
        return (p.expiresAt - clock()).coerceAtLeast(0)
    }

    // ---------- internal ----------
    private suspend fun newOrder(userId: Long, kind: OrderKind, subtotal: Long, hubId: Long?, deadline: Long): Long {
        val now = clock()
        val id = orders.insert(OrderEntity(
            code = "TMP-$now-${(0..99_999).random()}", userId = userId, kind = kind, subtotal = subtotal,
            total = subtotal, hubId = hubId, pickupDeadline = deadline,
            paymentExpiresAt = now + PAYMENT_WINDOW_MS, createdAt = now
        ))
        val ym = SimpleDateFormat("yyyyMM", Locale.US).format(Date(now))
        orders.setCodes(id, (if (kind == OrderKind.OLEH_OLEH) "ORD-BMS-" else "TKT-") + id, "TRX-BMS-$ym-$id")
        return id
    }
}
