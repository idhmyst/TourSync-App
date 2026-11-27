package putri.data.repo

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import putri.data.PutriDatabase
import putri.data.entity.*

class WalletRepository(private val db: PutriDatabase, private val clock: Clock = systemClock) {
    private val users = db.userDao()
    private val wallet = db.walletDao()

    companion object {
        const val MIN_TOPUP = 10_000L
        const val MAX_TOPUP = 5_000_000L
        const val POINTS_PER_LEVEL = 200
    }

    fun transactions(userId: Long): Flow<List<WalletTxEntity>> = wallet.observeTx(userId)
    fun pointHistory(userId: Long): Flow<List<EcoPointTxEntity>> = wallet.observePoints(userId)

    /** Top up (QRIS / VA disimulasikan: langsung sukses). Mengembalikan saldo baru. */
    suspend fun topUp(userId: Long, amount: Long, via: PaymentMethod = PaymentMethod.QRIS): Result<Long> = runCatching {
        require(amount in MIN_TOPUP..MAX_TOPUP) { "Nominal top up Rp10.000 - Rp5.000.000" }
        require(via != PaymentMethod.ECO_WALLET && via != PaymentMethod.GROUP_POOL) { "Metode top up tidak valid" }
        db.withTransaction {
            requireNotNull(users.byId(userId)) { "Akun tidak ditemukan" }
            users.credit(userId, amount)
            val bal = users.byId(userId)!!.walletBalance
            wallet.insertTx(WalletTxEntity(userId = userId, type = WalletTxType.TOPUP, amount = amount, balanceAfter = bal, ref = via.name, createdAt = clock()))
            bal
        }
    }

    /** Level Eco-Explorer dihitung dari total poin yang pernah didapat. */
    suspend fun ecoLevel(userId: Long): Int = 1 + wallet.lifetimePoints(userId) / POINTS_PER_LEVEL
}
