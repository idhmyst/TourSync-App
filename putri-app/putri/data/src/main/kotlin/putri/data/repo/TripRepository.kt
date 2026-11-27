package putri.data.repo

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import putri.core.splitbill.Expense
import putri.core.splitbill.ExpenseCategory
import putri.core.splitbill.GroupPool
import putri.core.splitbill.SplitBill
import putri.data.PutriDatabase
import putri.data.dao.MemberRow
import putri.data.entity.*

data class ExpenseInput(
    val title: String,
    val category: ExpenseCategory,
    val amount: Long,
    /** null = dibayar dari Kas Bersama; isi userId = talangan anggota itu. */
    val paidByUserId: Long?,
    val splitMode: SplitMode = SplitMode.EQUAL,
    /** EQUAL: siapa saja yang ikut (kosong = semua anggota). */
    val participantIds: List<Long> = emptyList(),
    /** CUSTOM: userId -> nominal. Total harus = amount. */
    val customShares: Map<Long, Long> = emptyMap(),
    val paymentMethod: String? = null,
    val note: String = "",
    val receiptPath: String? = null
)

data class PoolStatus(val collected: Long, val spent: Long) {
    val remaining get() = collected - spent
    val view get() = GroupPool(collected, spent)
}

/** Grup trip: anggota, kas bersama, catatan pengeluaran, split-bill & settle-up. */
class TripRepository(private val db: PutriDatabase, private val clock: Clock = systemClock) {
    private val trips = db.tripDao()
    private val users = db.userDao()
    private val wallet = db.walletDao()

    // ---------- baca ----------
    fun myTrips(userId: Long, status: TripStatus = TripStatus.ACTIVE) = trips.observeTripsOf(userId, status)
    fun trip(id: Long) = trips.observeTrip(id)
    fun members(tripId: Long): Flow<List<MemberRow>> = trips.observeMembers(tripId)
    fun expenses(tripId: Long) = trips.observeExpenses(tripId)
    fun settlements(tripId: Long) = trips.observeSettlements(tripId)
    fun pool(tripId: Long): Flow<PoolStatus> =
        combine(trips.observeCollected(tripId), trips.observePoolSpent(tripId)) { c, s -> PoolStatus(c, s) }
    suspend fun shares(expenseId: Long) = trips.shares(expenseId)

    // ---------- trip ----------
    suspend fun createTrip(adminId: Long, name: String, description: String = "", startDate: Long? = null, endDate: Long? = null): Result<TripEntity> = runCatching {
        require(name.isNotBlank()) { "Nama grup wajib diisi" }
        require(startDate == null || endDate == null || endDate >= startDate) { "Tanggal selesai tidak boleh sebelum mulai" }
        db.withTransaction {
            requireNotNull(users.byId(adminId)) { "Akun tidak ditemukan" }
            val now = clock()
            val id = trips.insertTrip(TripEntity(name = name.trim(), description = description, adminUserId = adminId,
                startDate = startDate, endDate = endDate, createdAt = now))
            trips.insertMember(TripMemberEntity(id, adminId, TripRole.ADMIN, now))
            trips.trip(id)!!
        }
    }

    suspend fun updateTrip(requesterId: Long, tripId: Long, name: String, description: String, startDate: Long?, endDate: Long?) = runCatching {
        require(name.isNotBlank()) { "Nama grup wajib diisi" }
        require(startDate == null || endDate == null || endDate >= startDate) { "Tanggal selesai tidak boleh sebelum mulai" }
        val t = requireAdmin(requesterId, tripId)
        trips.updateTrip(t.copy(name = name.trim(), description = description, startDate = startDate, endDate = endDate))
    }

    suspend fun archive(requesterId: Long, tripId: Long) = runCatching {
        db.withTransaction {
            val t = requireAdmin(requesterId, tripId)
            require(trips.settlements(tripId).none { it.status != SettleStatus.LUNAS }) { "Masih ada utang yang belum lunas" }
            trips.updateTrip(t.copy(status = TripStatus.ARCHIVED))
        }
    }

    suspend fun deleteTrip(requesterId: Long, tripId: Long) = runCatching { trips.deleteTrip(requireAdmin(requesterId, tripId)) }

    // ---------- anggota ----------
    /** Undang lewat username / email / no HP yang sudah terdaftar. */
    suspend fun invite(requesterId: Long, tripId: Long, identifier: String): Result<Unit> = runCatching {
        db.withTransaction {
            requireActive(requireAdmin(requesterId, tripId))
            val u = requireNotNull(users.byIdentifier(identifier.trim())) { "Pengguna tidak ditemukan" }
            require(u.role == UserRole.WISATAWAN) { "Hanya akun Wisatawan yang bisa diundang" }
            require(trips.member(tripId, u.id) == null) { "${u.fullName} sudah menjadi anggota" }
            trips.insertMember(TripMemberEntity(tripId, u.id, TripRole.MEMBER, clock()))
            recompute(tripId)
        }
    }

    /** Admin mengeluarkan anggota, atau anggota keluar sendiri. Ditolak bila masih terlibat uang. */
    suspend fun removeMember(requesterId: Long, tripId: Long, userId: Long): Result<Unit> = runCatching {
        db.withTransaction {
            val t = requireNotNull(trips.trip(tripId)) { "Grup tidak ditemukan" }
            require(requesterId == userId || requesterId == t.adminUserId) { "Hanya admin yang bisa mengeluarkan anggota" }
            require(userId != t.adminUserId) { "Admin tidak bisa keluar; hapus atau arsipkan grup" }
            requireNotNull(trips.member(tripId, userId)) { "Bukan anggota grup ini" }
            val involved = trips.sharesOfTrip(tripId).any { it.userId == userId } ||
                trips.expenses(tripId).any { it.paidByUserId == userId } ||
                trips.contributedBy(tripId, userId) > 0 ||
                trips.settlements(tripId).any { it.fromUserId == userId || it.toUserId == userId }
            require(!involved) { "Anggota masih terlibat pengeluaran/kas/utang" }
            trips.removeMember(tripId, userId)
        }
    }

    // ---------- kas bersama ----------
    suspend fun contribute(userId: Long, tripId: Long, amount: Long): Result<PoolStatus> = runCatching {
        require(amount > 0) { "Nominal harus > 0" }
        db.withTransaction {
            requireActive(requireNotNull(trips.trip(tripId)) { "Grup tidak ditemukan" })
            requireNotNull(trips.member(tripId, userId)) { "Anda bukan anggota grup ini" }
            require(users.debit(userId, amount) == 1) { "Saldo Eco-Wallet tidak cukup" }
            val now = clock()
            wallet.insertTx(WalletTxEntity(userId = userId, type = WalletTxType.POOL_CONTRIBUTION, amount = amount,
                balanceAfter = users.byId(userId)!!.walletBalance, ref = "TRIP-$tripId", createdAt = now))
            trips.insertContribution(ContributionEntity(tripId = tripId, userId = userId, amount = amount, createdAt = now))
            PoolStatus(trips.collected(tripId), trips.poolSpent(tripId))
        }
    }

    // ---------- pengeluaran ----------
    suspend fun addExpense(requesterId: Long, tripId: Long, input: ExpenseInput): Result<GroupExpenseEntity> = runCatching {
        db.withTransaction {
            requireActive(requireNotNull(trips.trip(tripId)) { "Grup tidak ditemukan" })
            requireNotNull(trips.member(tripId, requesterId)) { "Anda bukan anggota grup ini" }
            val members = trips.memberIds(tripId)
            validate(input, members)
            val id = trips.insertExpense(GroupExpenseEntity(
                tripId = tripId, title = input.title.trim(), category = input.category, amount = input.amount,
                paidByUserId = input.paidByUserId, splitMode = input.splitMode, paymentMethod = input.paymentMethod,
                note = input.note, receiptPath = input.receiptPath, createdAt = clock()
            ))
            trips.insertShares(buildShares(members, input).map { (u, a) -> ExpenseShareEntity(id, u, a) })
            require(trips.collected(tripId) - trips.poolSpent(tripId) >= 0) { "Saldo kas bersama tidak cukup" }
            recompute(tripId)
            trips.expense(id)!!
        }
    }

    suspend fun updateExpense(requesterId: Long, expenseId: Long, input: ExpenseInput): Result<GroupExpenseEntity> = runCatching {
        db.withTransaction {
            val old = requireNotNull(trips.expense(expenseId)) { "Pengeluaran tidak ditemukan" }
            require(old.orderId == null) { "Pengeluaran dari pesanan tidak bisa diedit; batalkan pesanannya" }
            requireActive(requireNotNull(trips.trip(old.tripId)) { "Grup tidak ditemukan" })
            requireCanEdit(requesterId, old)
            val members = trips.memberIds(old.tripId)
            validate(input, members)
            trips.deleteShares(expenseId)
            trips.updateExpense(old.copy(
                title = input.title.trim(), category = input.category, amount = input.amount,
                paidByUserId = input.paidByUserId, splitMode = input.splitMode, paymentMethod = input.paymentMethod,
                note = input.note, receiptPath = input.receiptPath
            ))
            trips.insertShares(buildShares(members, input).map { (u, a) -> ExpenseShareEntity(expenseId, u, a) })
            require(trips.collected(old.tripId) - trips.poolSpent(old.tripId) >= 0) { "Saldo kas bersama tidak cukup" }
            recompute(old.tripId)
            trips.expense(expenseId)!!
        }
    }

    suspend fun deleteExpense(requesterId: Long, expenseId: Long): Result<Unit> = runCatching {
        db.withTransaction {
            val e = requireNotNull(trips.expense(expenseId)) { "Pengeluaran tidak ditemukan" }
            require(e.orderId == null) { "Pengeluaran dari pesanan tidak bisa dihapus; batalkan pesanannya" }
            requireActive(requireNotNull(trips.trip(e.tripId)) { "Grup tidak ditemukan" })
            requireCanEdit(requesterId, e)
            trips.deleteExpense(expenseId)
            recompute(e.tripId)
        }
    }

    // ---------- settle-up ----------
    /** Saldo bersih per anggota: positif = piutang, negatif = utang (setelah pelunasan yang tercatat). */
    suspend fun balances(tripId: Long): Map<Long, Long> = balancesInternal(tripId)

    /** Yang berutang menandai "sudah bayar" -> menunggu konfirmasi penerima. */
    suspend fun markPaid(userId: Long, settlementId: Long): Result<Unit> = runCatching {
        db.withTransaction {
            val s = requireNotNull(trips.settlement(settlementId)) { "Tagihan tidak ditemukan" }
            require(s.fromUserId == userId) { "Hanya yang berutang yang bisa menandai bayar" }
            require(s.status == SettleStatus.BELUM_LUNAS) { "Status tagihan sudah berubah" }
            trips.updateSettlement(s.copy(status = SettleStatus.KONFIRMASI, updatedAt = clock()))
            recompute(s.tripId)
        }
    }

    /** Penerima mengonfirmasi uang sudah masuk. */
    suspend fun confirm(userId: Long, settlementId: Long): Result<Unit> = runCatching {
        db.withTransaction {
            val s = requireNotNull(trips.settlement(settlementId)) { "Tagihan tidak ditemukan" }
            require(s.toUserId == userId) { "Hanya penerima yang bisa mengonfirmasi" }
            require(s.status == SettleStatus.KONFIRMASI) { "Belum ada pembayaran untuk dikonfirmasi" }
            trips.updateSettlement(s.copy(status = SettleStatus.LUNAS, updatedAt = clock()))
        }
    }

    /** Penerima menolak (uang belum masuk) -> kembali BELUM_LUNAS. */
    suspend fun reject(userId: Long, settlementId: Long): Result<Unit> = runCatching {
        db.withTransaction {
            val s = requireNotNull(trips.settlement(settlementId)) { "Tagihan tidak ditemukan" }
            require(s.toUserId == userId) { "Hanya penerima yang bisa menolak" }
            require(s.status == SettleStatus.KONFIRMASI) { "Tidak ada pembayaran untuk ditolak" }
            trips.updateSettlement(s.copy(status = SettleStatus.BELUM_LUNAS, updatedAt = clock()))
            recompute(s.tripId)
        }
    }

    /** Lunasi langsung lewat Eco-Wallet: saldo pindah ke penerima, status LUNAS. */
    suspend fun payViaWallet(userId: Long, settlementId: Long): Result<Unit> = runCatching {
        db.withTransaction {
            val s = requireNotNull(trips.settlement(settlementId)) { "Tagihan tidak ditemukan" }
            require(s.fromUserId == userId) { "Bukan tagihan Anda" }
            require(s.status == SettleStatus.BELUM_LUNAS) { "Status tagihan sudah berubah" }
            require(users.debit(userId, s.amount) == 1) { "Saldo Eco-Wallet tidak cukup" }
            users.credit(s.toUserId, s.amount)
            val now = clock()
            wallet.insertTx(WalletTxEntity(userId = userId, type = WalletTxType.SETTLEMENT, amount = s.amount,
                balanceAfter = users.byId(userId)!!.walletBalance, ref = "SETTLE-${s.id}", createdAt = now))
            wallet.insertTx(WalletTxEntity(userId = s.toUserId, type = WalletTxType.SETTLEMENT_IN, amount = s.amount,
                balanceAfter = users.byId(s.toUserId)!!.walletBalance, ref = "SETTLE-${s.id}", createdAt = now))
            trips.updateSettlement(s.copy(status = SettleStatus.LUNAS, updatedAt = now))
        }
    }

    // ---------- internal ----------
    private suspend fun balancesInternal(tripId: Long): Map<Long, Long> {
        val members = trips.memberIds(tripId)
        val sharesBy = trips.sharesOfTrip(tripId).groupBy { it.expenseId }
        val core = trips.expenses(tripId).filter { it.paidByUserId != null }.map { e ->
            Expense(e.id.toString(), e.title, e.category, e.amount, e.paidByUserId.toString(),
                sharesBy[e.id].orEmpty().associate { it.userId.toString() to it.amount })
        }
        val bal = SplitBill.netBalances(core, members.map { it.toString() }).toMutableMap()
        // pelunasan yang sudah dibayar / menunggu konfirmasi dianggap mengurangi utang
        trips.settlements(tripId).filter { it.status != SettleStatus.BELUM_LUNAS }.forEach { s ->
            val f = s.fromUserId.toString(); val t = s.toUserId.toString()
            bal[f] = (bal[f] ?: 0L) + s.amount
            bal[t] = (bal[t] ?: 0L) - s.amount
        }
        return bal.mapKeys { it.key.toLong() }
    }

    /** Hitung ulang tagihan (utang paling sederhana). Baris LUNAS/KONFIRMASI dipertahankan. */
    private suspend fun recompute(tripId: Long) {
        val bal = balancesInternal(tripId).mapKeys { it.key.toString() }
        trips.clearPendingSettlements(tripId)
        val now = clock()
        SplitBill.settle(bal).forEach { t ->
            trips.insertSettlement(SettlementEntity(tripId = tripId, fromUserId = t.from.toLong(), toUserId = t.to.toLong(),
                amount = t.amount, status = SettleStatus.BELUM_LUNAS, updatedAt = now))
        }
    }

    private fun validate(i: ExpenseInput, members: List<Long>) {
        require(i.title.isNotBlank()) { "Nama pengeluaran wajib diisi" }
        require(i.amount > 0) { "Nominal harus > 0" }
        require(i.paidByUserId == null || i.paidByUserId in members) { "Pembayar harus anggota grup" }
    }

    private fun buildShares(members: List<Long>, i: ExpenseInput): Map<Long, Long> = when (i.splitMode) {
        SplitMode.EQUAL -> {
            val ps = i.participantIds.ifEmpty { members }
            require(members.containsAll(ps)) { "Peserta harus anggota grup" }
            SplitBill.splitEqually(i.amount, ps.map { it.toString() }).mapKeys { it.key.toLong() }
        }
        SplitMode.CUSTOM -> {
            require(i.customShares.isNotEmpty()) { "Isi bagian tiap anggota" }
            require(members.containsAll(i.customShares.keys)) { "Peserta harus anggota grup" }
            require(i.customShares.values.all { it > 0 }) { "Bagian harus > 0" }
            require(i.customShares.values.sum() == i.amount) { "Total bagian harus sama dengan nominal" }
            i.customShares
        }
    }

    private suspend fun requireAdmin(requesterId: Long, tripId: Long): TripEntity {
        val t = requireNotNull(trips.trip(tripId)) { "Grup tidak ditemukan" }
        require(t.adminUserId == requesterId) { "Hanya admin grup yang boleh" }
        return t
    }
    private fun requireActive(t: TripEntity) = require(t.status == TripStatus.ACTIVE) { "Grup sudah diarsipkan" }
    private suspend fun requireCanEdit(requesterId: Long, e: GroupExpenseEntity) {
        val t = requireNotNull(trips.trip(e.tripId)) { "Grup tidak ditemukan" }
        require(requesterId == t.adminUserId || requesterId == e.paidByUserId) { "Hanya admin atau pembayar yang boleh mengubah" }
    }
}
