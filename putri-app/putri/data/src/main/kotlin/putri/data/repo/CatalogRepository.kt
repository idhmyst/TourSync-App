package putri.data.repo

import kotlinx.coroutines.flow.Flow
import putri.data.PutriDatabase
import putri.data.entity.*

/**
 * CRUD katalog: destinasi, paket tiket, menu kuliner, mitra UMKM + produk oleh-oleh,
 * serta Pickup Hub + loker.
 * Produk hanya boleh diubah pemilik mitranya; hub/loker hanya oleh Petugas Hub.
 */
class CatalogRepository(private val db: PutriDatabase) {
    private val cat = db.catalogDao()
    private val prod = db.productDao()
    private val hub = db.hubDao()
    private val users = db.userDao()

    // ================= Destinasi =================
    fun destinations(): Flow<List<DestinationEntity>> = cat.observeDestinations()
    suspend fun searchDestinations(q: String) = cat.searchDestinations(q.trim())
    suspend fun destination(id: Long) = cat.destination(id)
    fun tickets(destinationId: Long) = cat.observeTickets(destinationId)

    suspend fun addDestination(d: DestinationEntity) = runCatching { validate(d); cat.insertDestination(d.copy(id = 0)) }
    suspend fun updateDestination(d: DestinationEntity) = runCatching {
        requireNotNull(cat.destination(d.id)) { "Destinasi tidak ditemukan" }; validate(d); cat.updateDestination(d)
    }
    /** Hapus permanen; paket tiketnya ikut terhapus. Untuk menyembunyikan saja pakai active=false. */
    suspend fun deleteDestination(id: Long) = runCatching {
        cat.deleteDestination(requireNotNull(cat.destination(id)) { "Destinasi tidak ditemukan" })
    }

    suspend fun addTicket(t: TicketPackageEntity) = runCatching {
        requireNotNull(cat.destination(t.destinationId)) { "Destinasi tidak ditemukan" }
        require(t.name.isNotBlank() && t.price >= 0) { "Nama/harga paket tidak valid" }
        cat.insertTicket(t.copy(id = 0))
    }
    suspend fun updateTicket(t: TicketPackageEntity) = runCatching {
        requireNotNull(cat.ticket(t.id)) { "Paket tidak ditemukan" }
        require(t.name.isNotBlank() && t.price >= 0) { "Nama/harga paket tidak valid" }
        cat.updateTicket(t)
    }
    suspend fun deleteTicket(id: Long) = runCatching { cat.deleteTicket(requireNotNull(cat.ticket(id)) { "Paket tidak ditemukan" }) }

    private fun validate(d: DestinationEntity) {
        require(d.name.isNotBlank()) { "Nama destinasi wajib diisi" }
        require(d.ticketPrice >= 0) { "Harga tiket tidak valid" }
        require(d.rating in 0.0..5.0) { "Rating harus 0-5" }
        require(Regex("^\\d{2}:\\d{2}$").matches(d.openTime) && Regex("^\\d{2}:\\d{2}$").matches(d.closeTime)) { "Format jam HH:mm" }
    }

    // ================= Menu kuliner =================
    fun meals() = cat.observeMeals()
    suspend fun addMeal(m: MealEntity) = runCatching {
        require(m.name.isNotBlank() && m.pricePerPerson > 0) { "Nama/harga menu tidak valid" }; cat.insertMeal(m.copy(id = 0))
    }
    suspend fun updateMeal(m: MealEntity) = runCatching {
        requireNotNull(cat.meal(m.id)) { "Menu tidak ditemukan" }
        require(m.name.isNotBlank() && m.pricePerPerson > 0) { "Nama/harga menu tidak valid" }; cat.updateMeal(m)
    }
    suspend fun deleteMeal(id: Long) = runCatching { cat.deleteMeal(requireNotNull(cat.meal(id)) { "Menu tidak ditemukan" }) }

    // ================= Mitra UMKM & produk =================
    fun products() = prod.observeActive()
    fun productCategories() = prod.observeCategories()
    fun productsByCategory(category: String) = prod.observeByCategory(category)
    fun productsOfPartner(partnerId: Long) = prod.observeByPartner(partnerId)
    suspend fun searchProducts(q: String) = prod.search(q.trim())
    suspend fun product(id: Long) = prod.byId(id)
    suspend fun partnersOf(ownerUserId: Long) = prod.partnersOf(ownerUserId)

    suspend fun addPartner(actorId: Long, name: String, address: String = "") = runCatching {
        requireRole(actorId, UserRole.MITRA_UMKM)
        require(name.isNotBlank()) { "Nama usaha wajib diisi" }
        prod.insertPartner(PartnerEntity(name = name.trim(), address = address, ownerUserId = actorId))
    }
    suspend fun updatePartner(actorId: Long, p: PartnerEntity) = runCatching {
        requireOwner(actorId, p.id); require(p.name.isNotBlank()) { "Nama usaha wajib diisi" }
        prod.updatePartner(p.copy(ownerUserId = actorId))
    }
    suspend fun deletePartner(actorId: Long, partnerId: Long) = runCatching {
        requireOwner(actorId, partnerId); prod.deletePartner(prod.partner(partnerId)!!)
    }

    suspend fun addProduct(actorId: Long, p: ProductEntity) = runCatching {
        requireOwner(actorId, p.partnerId); validate(p); prod.insert(p.copy(id = 0))
    }
    suspend fun updateProduct(actorId: Long, p: ProductEntity) = runCatching {
        val old = requireNotNull(prod.byId(p.id)) { "Produk tidak ditemukan" }
        requireOwner(actorId, old.partnerId)
        require(p.partnerId == old.partnerId) { "Produk tidak bisa dipindah ke usaha lain" }
        validate(p); prod.update(p)
    }
    suspend fun setStock(actorId: Long, productId: Long, stock: Int) = runCatching {
        require(stock >= 0) { "Stok tidak boleh negatif" }
        val old = requireNotNull(prod.byId(productId)) { "Produk tidak ditemukan" }
        requireOwner(actorId, old.partnerId); prod.update(old.copy(stock = stock))
    }
    suspend fun deleteProduct(actorId: Long, productId: Long) = runCatching {
        val old = requireNotNull(prod.byId(productId)) { "Produk tidak ditemukan" }
        requireOwner(actorId, old.partnerId); prod.delete(old)
    }

    private fun validate(p: ProductEntity) {
        require(p.name.isNotBlank()) { "Nama produk wajib diisi" }
        require(p.category.isNotBlank()) { "Kategori wajib diisi" }
        require(p.price > 0) { "Harga harus > 0" }
        require(p.stock >= 0) { "Stok tidak boleh negatif" }
    }

    // ================= Pickup Hub & loker =================
    fun hubs() = hub.observeHubs()
    fun lockers(hubId: Long) = hub.observeLockers(hubId)
    fun freeLockerCount(hubId: Long) = hub.observeFreeCount(hubId)

    suspend fun addHub(actorId: Long, h: HubEntity) = runCatching {
        requireRole(actorId, UserRole.PETUGAS_HUB); validate(h); hub.insertHub(h.copy(id = 0))
    }
    suspend fun updateHub(actorId: Long, h: HubEntity) = runCatching {
        requireRole(actorId, UserRole.PETUGAS_HUB); requireNotNull(hub.hub(h.id)) { "Hub tidak ditemukan" }; validate(h); hub.updateHub(h)
    }
    suspend fun deleteHub(actorId: Long, hubId: Long) = runCatching {
        requireRole(actorId, UserRole.PETUGAS_HUB); hub.deleteHub(requireNotNull(hub.hub(hubId)) { "Hub tidak ditemukan" })
    }
    suspend fun addLocker(actorId: Long, hubId: Long, code: String) = runCatching {
        requireRole(actorId, UserRole.PETUGAS_HUB)
        requireNotNull(hub.hub(hubId)) { "Hub tidak ditemukan" }
        require(code.isNotBlank()) { "Kode loker wajib diisi" }
        require(hub.lockerByCode(code.trim()) == null) { "Kode loker sudah ada" }
        hub.insertLocker(LockerEntity(hubId = hubId, code = code.trim()))
    }
    suspend fun setLockerStatus(actorId: Long, code: String, status: LockerStatus) = runCatching {
        requireRole(actorId, UserRole.PETUGAS_HUB)
        val l = requireNotNull(hub.lockerByCode(code)) { "Loker tidak ditemukan" }
        require(l.status != LockerStatus.OCCUPIED || status == LockerStatus.OCCUPIED) { "Loker sedang terisi pesanan" }
        hub.setLockerStatus(code, status)
    }
    suspend fun deleteLocker(actorId: Long, code: String) = runCatching {
        requireRole(actorId, UserRole.PETUGAS_HUB)
        val l = requireNotNull(hub.lockerByCode(code)) { "Loker tidak ditemukan" }
        require(l.status != LockerStatus.OCCUPIED) { "Loker sedang terisi pesanan" }
        hub.deleteLocker(l)
    }

    private fun validate(h: HubEntity) {
        require(h.name.isNotBlank() && h.address.isNotBlank()) { "Nama dan alamat hub wajib diisi" }
        require(Regex("^\\d{2}:\\d{2}$").matches(h.openTime) && Regex("^\\d{2}:\\d{2}$").matches(h.closeTime)) { "Format jam HH:mm" }
    }

    // ---- otorisasi ----
    private suspend fun requireRole(actorId: Long, role: UserRole) {
        val u = users.byId(actorId)
        require(u != null && u.role == role) { "Aksi ini khusus ${role.name.lowercase().replace('_', ' ')}" }
    }
    private suspend fun requireOwner(actorId: Long, partnerId: Long) {
        requireRole(actorId, UserRole.MITRA_UMKM)
        val p = requireNotNull(prod.partner(partnerId)) { "Usaha tidak ditemukan" }
        require(p.ownerUserId == actorId) { "Bukan usaha milik Anda" }
    }
}
