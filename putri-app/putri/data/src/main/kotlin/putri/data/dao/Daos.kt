package putri.data.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import putri.data.entity.*

@Dao
interface UserDao {
    @Insert suspend fun insert(u: UserEntity): Long
    @Update suspend fun update(u: UserEntity)
    @Delete suspend fun delete(u: UserEntity)
    @Query("SELECT * FROM users WHERE id=:id") suspend fun byId(id: Long): UserEntity?
    @Query("SELECT * FROM users WHERE id=:id") fun observe(id: Long): Flow<UserEntity?>
    @Query("SELECT * FROM users WHERE lower(username)=lower(:x) OR lower(email)=lower(:x) OR phone=:x LIMIT 1")
    suspend fun byIdentifier(x: String): UserEntity?
    @Query("SELECT COUNT(*) FROM users WHERE lower(username)=lower(:u) OR lower(email)=lower(:e)")
    suspend fun countConflicts(u: String, e: String): Int
    @Query("SELECT COUNT(*) FROM users") suspend fun count(): Int
    @Query("UPDATE users SET publicCode=:code WHERE id=:id") suspend fun setPublicCode(id: Long, code: String)
    @Query("UPDATE users SET walletBalance = walletBalance + :amt WHERE id=:id") suspend fun credit(id: Long, amt: Long)
    /** Atomik: gagal (0 baris) kalau saldo kurang. */
    @Query("UPDATE users SET walletBalance = walletBalance - :amt WHERE id=:id AND walletBalance >= :amt")
    suspend fun debit(id: Long, amt: Long): Int
    @Query("UPDATE users SET ecoPoints = ecoPoints + :d WHERE id=:id AND ecoPoints + :d >= 0")
    suspend fun addPoints(id: Long, d: Int): Int
}

@Dao
interface CatalogDao {
    // destinasi
    @Insert suspend fun insertDestination(d: DestinationEntity): Long
    @Update suspend fun updateDestination(d: DestinationEntity)
    @Delete suspend fun deleteDestination(d: DestinationEntity)
    @Query("SELECT * FROM destinations WHERE id=:id") suspend fun destination(id: Long): DestinationEntity?
    @Query("SELECT * FROM destinations WHERE active=1 ORDER BY rating DESC, name") fun observeDestinations(): Flow<List<DestinationEntity>>
    @Query("SELECT * FROM destinations WHERE active=1") suspend fun activeDestinations(): List<DestinationEntity>
    @Query("SELECT * FROM destinations WHERE active=1 AND (name LIKE '%'||:q||'%' OR area LIKE '%'||:q||'%') ORDER BY rating DESC")
    suspend fun searchDestinations(q: String): List<DestinationEntity>
    @Query("SELECT COUNT(*) FROM destinations") suspend fun destinationCount(): Int

    // paket tiket
    @Insert suspend fun insertTicket(t: TicketPackageEntity): Long
    @Update suspend fun updateTicket(t: TicketPackageEntity)
    @Delete suspend fun deleteTicket(t: TicketPackageEntity)
    @Query("SELECT * FROM ticket_packages WHERE id=:id") suspend fun ticket(id: Long): TicketPackageEntity?
    @Query("SELECT * FROM ticket_packages WHERE destinationId=:destId AND active=1 ORDER BY price")
    fun observeTickets(destId: Long): Flow<List<TicketPackageEntity>>

    // menu kuliner
    @Insert suspend fun insertMeal(m: MealEntity): Long
    @Update suspend fun updateMeal(m: MealEntity)
    @Delete suspend fun deleteMeal(m: MealEntity)
    @Query("SELECT * FROM meals WHERE id=:id") suspend fun meal(id: Long): MealEntity?
    @Query("SELECT * FROM meals WHERE active=1 ORDER BY name") fun observeMeals(): Flow<List<MealEntity>>
    @Query("SELECT * FROM meals WHERE active=1") suspend fun activeMeals(): List<MealEntity>
}

@Dao
interface ProductDao {
    @Insert suspend fun insertPartner(p: PartnerEntity): Long
    @Update suspend fun updatePartner(p: PartnerEntity)
    @Delete suspend fun deletePartner(p: PartnerEntity)
    @Query("SELECT * FROM partners") suspend fun partners(): List<PartnerEntity>
    @Query("SELECT * FROM partners WHERE id=:id") suspend fun partner(id: Long): PartnerEntity?
    @Query("SELECT * FROM partners WHERE ownerUserId=:userId") suspend fun partnersOf(userId: Long): List<PartnerEntity>

    @Insert suspend fun insert(p: ProductEntity): Long
    @Update suspend fun update(p: ProductEntity)
    @Delete suspend fun delete(p: ProductEntity)
    @Query("SELECT * FROM products WHERE id=:id") suspend fun byId(id: Long): ProductEntity?
    @Query("SELECT * FROM products WHERE active=1 ORDER BY bestSeller DESC, name") fun observeActive(): Flow<List<ProductEntity>>
    @Query("SELECT * FROM products WHERE active=1 AND category=:cat ORDER BY name") fun observeByCategory(cat: String): Flow<List<ProductEntity>>
    @Query("SELECT DISTINCT category FROM products WHERE active=1 ORDER BY category") fun observeCategories(): Flow<List<String>>
    @Query("SELECT * FROM products WHERE partnerId=:partnerId ORDER BY name") fun observeByPartner(partnerId: Long): Flow<List<ProductEntity>>
    @Query("SELECT * FROM products WHERE active=1 AND stock>0") suspend fun inStock(): List<ProductEntity>
    @Query("SELECT * FROM products WHERE active=1 AND name LIKE '%'||:q||'%'") suspend fun search(q: String): List<ProductEntity>

    /** Reservasi stok atomik; 0 = gagal (stok kurang / produk nonaktif). */
    @Query("UPDATE products SET stock = stock - :q WHERE id=:id AND stock >= :q AND active=1")
    suspend fun reserve(id: Long, q: Int): Int
    @Query("UPDATE products SET stock = stock + :q WHERE id=:id") suspend fun release(id: Long, q: Int)
}

data class CartRow(
    val productId: Long,
    val qty: Int,
    val name: String,
    val variant: String,
    val packaging: String,
    val price: Long,
    val stock: Int,
    val partnerName: String
)

@Dao
interface CartDao {
    @Query("SELECT * FROM cart_items WHERE userId=:userId AND productId=:productId")
    suspend fun item(userId: Long, productId: Long): CartItemEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(i: CartItemEntity)
    @Query("DELETE FROM cart_items WHERE userId=:userId AND productId=:productId") suspend fun remove(userId: Long, productId: Long)
    @Query("DELETE FROM cart_items WHERE userId=:userId") suspend fun clear(userId: Long)

    @Query("""
        SELECT c.productId AS productId, c.qty AS qty, p.name AS name, p.variant AS variant, p.packaging AS packaging,
               p.price AS price, p.stock AS stock, pa.name AS partnerName
        FROM cart_items c JOIN products p ON p.id=c.productId JOIN partners pa ON pa.id=p.partnerId
        WHERE c.userId=:userId ORDER BY c.addedAt""")
    fun observeRows(userId: Long): Flow<List<CartRow>>

    @Query("""
        SELECT c.productId AS productId, c.qty AS qty, p.name AS name, p.variant AS variant, p.packaging AS packaging,
               p.price AS price, p.stock AS stock, pa.name AS partnerName
        FROM cart_items c JOIN products p ON p.id=c.productId JOIN partners pa ON pa.id=p.partnerId
        WHERE c.userId=:userId ORDER BY c.addedAt""")
    suspend fun rows(userId: Long): List<CartRow>
}

@Dao
interface HubDao {
    @Insert suspend fun insertHub(h: HubEntity): Long
    @Update suspend fun updateHub(h: HubEntity)
    @Delete suspend fun deleteHub(h: HubEntity)
    @Query("SELECT * FROM hubs WHERE id=:id") suspend fun hub(id: Long): HubEntity?
    @Query("SELECT * FROM hubs WHERE active=1 ORDER BY name") fun observeHubs(): Flow<List<HubEntity>>
    @Query("SELECT COUNT(*) FROM hubs") suspend fun hubCount(): Int

    @Insert suspend fun insertLocker(l: LockerEntity): Long
    @Update suspend fun updateLocker(l: LockerEntity)
    @Delete suspend fun deleteLocker(l: LockerEntity)
    @Query("SELECT * FROM lockers WHERE code=:code") suspend fun lockerByCode(code: String): LockerEntity?
    @Query("SELECT * FROM lockers WHERE hubId=:hubId ORDER BY code") fun observeLockers(hubId: Long): Flow<List<LockerEntity>>
    @Query("SELECT * FROM lockers WHERE hubId=:hubId AND status='FREE' ORDER BY code") suspend fun freeLockers(hubId: Long): List<LockerEntity>
    @Query("SELECT COUNT(*) FROM lockers WHERE hubId=:hubId AND status='FREE'") fun observeFreeCount(hubId: Long): Flow<Int>
    @Query("UPDATE lockers SET status=:status WHERE code=:code") suspend fun setLockerStatus(code: String, status: LockerStatus)
}

@Dao
interface OrderDao {
    @Insert suspend fun insert(o: OrderEntity): Long
    @Update suspend fun update(o: OrderEntity)
    @Query("UPDATE orders SET code=:code, trxCode=:trx WHERE id=:id") suspend fun setCodes(id: Long, code: String, trx: String)
    @Query("SELECT * FROM orders WHERE id=:id") suspend fun byId(id: Long): OrderEntity?
    @Query("SELECT * FROM orders WHERE code=:code") suspend fun byCode(code: String): OrderEntity?
    @Query("SELECT * FROM orders WHERE userId=:userId ORDER BY createdAt DESC") fun observeByUser(userId: Long): Flow<List<OrderEntity>>
    @Query("SELECT * FROM orders WHERE userId=:userId AND status=:status ORDER BY createdAt DESC")
    fun observeByUserAndStatus(userId: Long, status: OrderStatus): Flow<List<OrderEntity>>
    /** Antrian kerja Mitra UMKM: pesanan yang memuat produk milik mitranya. */
    @Query("""
        SELECT DISTINCT o.* FROM orders o
        JOIN order_items i ON i.orderId=o.id JOIN products p ON p.id=i.productId JOIN partners pa ON pa.id=p.partnerId
        WHERE pa.ownerUserId=:ownerId AND o.status=:status ORDER BY o.createdAt""")
    fun observeForPartner(ownerId: Long, status: OrderStatus): Flow<List<OrderEntity>>
    @Query("SELECT * FROM orders WHERE status='WAITING_PAYMENT' AND paymentExpiresAt < :now") suspend fun overduePayments(now: Long): List<OrderEntity>
    @Query("""SELECT o.* FROM orders o JOIN pickup_passes p ON p.orderId=o.id
              WHERE o.status='READY_PICKUP' AND p.expiresAt < :now""")
    suspend fun overduePickups(now: Long): List<OrderEntity>
    @Query("SELECT COALESCE(SUM(total),0) FROM orders WHERE status IN ('PAID','READY_PICKUP','COMPLETED')") suspend fun revenue(): Long

    @Insert suspend fun insertItem(i: OrderItemEntity): Long
    @Query("SELECT * FROM order_items WHERE orderId=:orderId") suspend fun items(orderId: Long): List<OrderItemEntity>
    @Query("SELECT * FROM order_items WHERE orderId=:orderId") fun observeItems(orderId: Long): Flow<List<OrderItemEntity>>

    @Insert suspend fun insertPayment(p: PaymentEntity): Long
    @Update suspend fun updatePayment(p: PaymentEntity)
    @Query("SELECT * FROM payments WHERE orderId=:orderId ORDER BY id DESC LIMIT 1") suspend fun paymentOf(orderId: Long): PaymentEntity?

    @Insert suspend fun insertPass(p: PickupPassEntity): Long
    @Update suspend fun updatePass(p: PickupPassEntity)
    @Query("SELECT * FROM pickup_passes WHERE orderId=:orderId") suspend fun passOf(orderId: Long): PickupPassEntity?
    @Query("SELECT * FROM pickup_passes WHERE orderId=:orderId") fun observePassOf(orderId: Long): Flow<PickupPassEntity?>
    @Query("SELECT * FROM pickup_passes WHERE token=:token") suspend fun passByToken(token: String): PickupPassEntity?
    @Query("SELECT * FROM pickup_passes WHERE backupCode=:code") suspend fun passByBackup(code: String): PickupPassEntity?
}

@Dao
interface WalletDao {
    @Insert suspend fun insertTx(t: WalletTxEntity): Long
    @Query("SELECT * FROM wallet_tx WHERE userId=:userId ORDER BY createdAt DESC, id DESC") fun observeTx(userId: Long): Flow<List<WalletTxEntity>>
    @Insert suspend fun insertPoints(t: EcoPointTxEntity): Long
    @Query("SELECT * FROM eco_point_tx WHERE userId=:userId ORDER BY createdAt DESC, id DESC") fun observePoints(userId: Long): Flow<List<EcoPointTxEntity>>
    @Query("SELECT COALESCE(SUM(delta),0) FROM eco_point_tx WHERE userId=:userId AND delta>0") suspend fun lifetimePoints(userId: Long): Int
}

data class MemberRow(val userId: Long, val fullName: String, val role: TripRole)

@Dao
interface TripDao {
    @Insert suspend fun insertTrip(t: TripEntity): Long
    @Update suspend fun updateTrip(t: TripEntity)
    @Delete suspend fun deleteTrip(t: TripEntity)
    @Query("SELECT * FROM trips WHERE id=:id") suspend fun trip(id: Long): TripEntity?
    @Query("SELECT * FROM trips WHERE id=:id") fun observeTrip(id: Long): Flow<TripEntity?>
    @Query("""SELECT t.* FROM trips t JOIN trip_members m ON m.tripId=t.id WHERE m.userId=:userId AND t.status=:status
              ORDER BY t.createdAt DESC""")
    fun observeTripsOf(userId: Long, status: TripStatus): Flow<List<TripEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertMember(m: TripMemberEntity): Long
    @Query("DELETE FROM trip_members WHERE tripId=:tripId AND userId=:userId") suspend fun removeMember(tripId: Long, userId: Long)
    @Query("SELECT * FROM trip_members WHERE tripId=:tripId AND userId=:userId") suspend fun member(tripId: Long, userId: Long): TripMemberEntity?
    @Query("SELECT userId FROM trip_members WHERE tripId=:tripId ORDER BY joinedAt, userId") suspend fun memberIds(tripId: Long): List<Long>
    @Query("""SELECT m.userId AS userId, u.fullName AS fullName, m.role AS role FROM trip_members m
              JOIN users u ON u.id=m.userId WHERE m.tripId=:tripId ORDER BY m.joinedAt, m.userId""")
    fun observeMembers(tripId: Long): Flow<List<MemberRow>>

    @Insert suspend fun insertContribution(c: ContributionEntity): Long
    @Query("SELECT COALESCE(SUM(amount),0) FROM pool_contributions WHERE tripId=:tripId AND userId=:userId")
    suspend fun contributedBy(tripId: Long, userId: Long): Long
    @Query("SELECT COALESCE(SUM(amount),0) FROM pool_contributions WHERE tripId=:tripId") suspend fun collected(tripId: Long): Long
    @Query("SELECT COALESCE(SUM(amount),0) FROM pool_contributions WHERE tripId=:tripId") fun observeCollected(tripId: Long): Flow<Long>

    @Insert suspend fun insertExpense(e: GroupExpenseEntity): Long
    @Update suspend fun updateExpense(e: GroupExpenseEntity)
    @Query("DELETE FROM group_expenses WHERE id=:id") suspend fun deleteExpense(id: Long)
    @Query("DELETE FROM group_expenses WHERE orderId=:orderId") suspend fun deleteExpenseByOrder(orderId: Long)
    @Query("SELECT * FROM group_expenses WHERE id=:id") suspend fun expense(id: Long): GroupExpenseEntity?
    @Query("SELECT * FROM group_expenses WHERE tripId=:tripId ORDER BY createdAt DESC, id DESC") fun observeExpenses(tripId: Long): Flow<List<GroupExpenseEntity>>
    @Query("SELECT * FROM group_expenses WHERE tripId=:tripId") suspend fun expenses(tripId: Long): List<GroupExpenseEntity>
    @Query("SELECT COALESCE(SUM(amount),0) FROM group_expenses WHERE tripId=:tripId AND paidByUserId IS NULL") suspend fun poolSpent(tripId: Long): Long
    @Query("SELECT COALESCE(SUM(amount),0) FROM group_expenses WHERE tripId=:tripId AND paidByUserId IS NULL") fun observePoolSpent(tripId: Long): Flow<Long>

    @Insert suspend fun insertShares(s: List<ExpenseShareEntity>)
    @Query("DELETE FROM expense_shares WHERE expenseId=:expenseId") suspend fun deleteShares(expenseId: Long)
    @Query("SELECT * FROM expense_shares WHERE expenseId=:expenseId") suspend fun shares(expenseId: Long): List<ExpenseShareEntity>
    @Query("""SELECT s.* FROM expense_shares s JOIN group_expenses e ON e.id=s.expenseId WHERE e.tripId=:tripId""")
    suspend fun sharesOfTrip(tripId: Long): List<ExpenseShareEntity>

    @Insert suspend fun insertSettlement(s: SettlementEntity): Long
    @Update suspend fun updateSettlement(s: SettlementEntity)
    @Query("SELECT * FROM settlements WHERE id=:id") suspend fun settlement(id: Long): SettlementEntity?
    @Query("SELECT * FROM settlements WHERE tripId=:tripId ORDER BY status, amount DESC") fun observeSettlements(tripId: Long): Flow<List<SettlementEntity>>
    @Query("SELECT * FROM settlements WHERE tripId=:tripId") suspend fun settlements(tripId: Long): List<SettlementEntity>
    @Query("DELETE FROM settlements WHERE tripId=:tripId AND status='BELUM_LUNAS'") suspend fun clearPendingSettlements(tripId: Long)
}

@Dao
interface BudgetPlanDao {
    @Insert suspend fun insert(p: BudgetPlanEntity): Long
    @Delete suspend fun delete(p: BudgetPlanEntity)
    @Query("SELECT * FROM budget_plans WHERE id=:id") suspend fun byId(id: Long): BudgetPlanEntity?
    @Query("SELECT * FROM budget_plans WHERE userId=:userId ORDER BY createdAt DESC") fun observeByUser(userId: Long): Flow<List<BudgetPlanEntity>>
}
