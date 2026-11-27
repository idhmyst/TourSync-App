package putri.data.repo

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import putri.data.PutriDatabase
import putri.data.dao.CartRow
import putri.data.entity.CartItemEntity

data class CartSummary(val rows: List<CartRow>) {
    val itemCount get() = rows.sumOf { it.qty }
    val total get() = rows.sumOf { it.price * it.qty }
}

/** Keranjang tersimpan otomatis di database (per pengguna). */
class CartRepository(private val db: PutriDatabase, private val clock: Clock = systemClock) {
    private val cart = db.cartDao()
    private val products = db.productDao()

    fun observe(userId: Long): Flow<CartSummary> = cart.observeRows(userId).map { CartSummary(it) }

    suspend fun add(userId: Long, productId: Long, qty: Int = 1): Result<Unit> = runCatching {
        require(qty > 0) { "Jumlah harus > 0" }
        db.withTransaction {
            val p = products.byId(productId)
            require(p != null && p.active) { "Produk tidak tersedia" }
            val newQty = (cart.item(userId, productId)?.qty ?: 0) + qty
            require(newQty <= p.stock) { "Stok '${p.name}' hanya ${p.stock}" }
            cart.upsert(CartItemEntity(userId, productId, newQty, clock()))
        }
    }

    /** Ubah jumlah; qty <= 0 menghapus item. */
    suspend fun setQty(userId: Long, productId: Long, qty: Int): Result<Unit> = runCatching {
        if (qty <= 0) { cart.remove(userId, productId); return@runCatching }
        db.withTransaction {
            val p = requireNotNull(products.byId(productId)) { "Produk tidak ditemukan" }
            require(qty <= p.stock) { "Stok '${p.name}' hanya ${p.stock}" }
            val old = cart.item(userId, productId)
            cart.upsert(CartItemEntity(userId, productId, qty, old?.addedAt ?: clock()))
        }
    }

    suspend fun remove(userId: Long, productId: Long) = cart.remove(userId, productId)
    suspend fun clear(userId: Long) = cart.clear(userId)
}
