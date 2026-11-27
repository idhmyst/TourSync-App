package putri.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import putri.core.budget.Duration
import putri.core.budget.TravelStyle
import putri.core.splitbill.ExpenseCategory

// ---------- enum ----------
enum class UserRole { WISATAWAN, MITRA_UMKM, PETUGAS_HUB }
enum class OrderKind { OLEH_OLEH, TIKET }
enum class OrderStatus { WAITING_PAYMENT, PAID, READY_PICKUP, COMPLETED, EXPIRED, CANCELLED }
enum class PaymentMethod { ECO_WALLET, GROUP_POOL, QRIS, VA_BCA, VA_MANDIRI, VA_BRI_BSI }
enum class PaymentStatus { SUCCESS, REFUNDED }
enum class PassKind { LOCKER, GATE }
enum class PassState { ACTIVE, USED, EXPIRED }
enum class LockerStatus { FREE, OCCUPIED, MAINTENANCE }
enum class WalletTxType { TOPUP, PAYMENT, REFUND, POOL_CONTRIBUTION, SETTLEMENT, SETTLEMENT_IN }
enum class SettleStatus { BELUM_LUNAS, KONFIRMASI, LUNAS }
enum class TripStatus { ACTIVE, ARCHIVED }
enum class TripRole { ADMIN, MEMBER }
enum class SplitMode { EQUAL, CUSTOM }

// ---------- akun ----------
@Entity(
    tableName = "users",
    indices = [Index("username", unique = true), Index("email", unique = true), Index("phone")]
)
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: UserRole,
    val publicCode: String = "",          // contoh: #BMS-9921
    val fullName: String,
    val username: String,
    val email: String,
    val phone: String,
    val passwordHash: String,
    val salt: String,
    val walletBalance: Long = 0,          // Eco-Wallet
    val ecoPoints: Int = 0,
    val allergens: String = "",           // "kacang,kedelai"
    val createdAt: Long
) {
    fun allergenList() = allergens.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
}

// ---------- katalog wisata ----------
@Entity(tableName = "destinations")
data class DestinationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val area: String,                     // "BATURRADEN TIMUR"
    val address: String,
    val description: String,
    val style: TravelStyle,
    val ticketPrice: Long,
    val openTime: String = "08:00",
    val closeTime: String = "17:00",
    val altitudeMdpl: Int? = null,
    val avgTempC: Int? = null,
    val rating: Double = 0.0,
    val reviewCount: Int = 0,
    val ecoCertified: Boolean = true,
    val badge: String = "",               // "Nature Reserve", "Wellness"
    val facilities: String = "",          // dipisah koma: "Mushola,Toilet,Parkir"
    val highlights: String = "",          // dipisah koma
    val active: Boolean = true
)

@Entity(
    tableName = "ticket_packages",
    foreignKeys = [ForeignKey(DestinationEntity::class, ["id"], ["destinationId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("destinationId")]
)
data class TicketPackageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val destinationId: Long,
    val name: String,
    val description: String = "",
    val price: Long,
    val unitLabel: String = "orang",      // "orang", "peluncuran"
    val popular: Boolean = false,
    val active: Boolean = true
)

@Entity(tableName = "meals")
data class MealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val partnerName: String,
    val pricePerPerson: Long,
    val active: Boolean = true
)

// ---------- UMKM & oleh-oleh ----------
@Entity(tableName = "partners", indices = [Index("ownerUserId")])
data class PartnerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val address: String = "",
    val ownerUserId: Long? = null         // akun Mitra UMKM pemilik
)

@Entity(
    tableName = "products",
    foreignKeys = [ForeignKey(PartnerEntity::class, ["id"], ["partnerId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("partnerId"), Index("category")]
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val partnerId: Long,
    val name: String,
    val variant: String = "",
    val category: String,                 // "Getuk Sokaraja", "Tempe & Olahan", ...
    val price: Long,
    val stock: Int,
    val packaging: String = "",           // "Vacuum Seal (Tahan 48 Jam)"
    val allergens: String = "",
    val preOrder: Boolean = true,         // Pre-Order H-1
    val bestSeller: Boolean = false,
    val active: Boolean = true
) {
    fun allergenList() = allergens.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
}

// ---------- Pickup Hub ----------
@Entity(tableName = "hubs")
data class HubEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val address: String,
    val description: String = "",
    val openTime: String = "17:00",
    val closeTime: String = "21:00",
    val temperatureControlled: Boolean = false,
    val active: Boolean = true
)

@Entity(
    tableName = "lockers",
    foreignKeys = [ForeignKey(HubEntity::class, ["id"], ["hubId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("hubId"), Index("code", unique = true)]
)
data class LockerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hubId: Long,
    val code: String,                     // "LOKER-08"
    val status: LockerStatus = LockerStatus.FREE
)

// ---------- keranjang ----------
@Entity(
    tableName = "cart_items",
    primaryKeys = ["userId", "productId"],
    foreignKeys = [
        ForeignKey(UserEntity::class, ["id"], ["userId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ProductEntity::class, ["id"], ["productId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("productId")]
)
data class CartItemEntity(val userId: Long, val productId: Long, val qty: Int, val addedAt: Long)

// ---------- pesanan & pembayaran ----------
@Entity(
    tableName = "orders",
    foreignKeys = [ForeignKey(UserEntity::class, ["id"], ["userId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("userId"), Index("code", unique = true), Index("status")]
)
data class OrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,                     // ORD-BMS-842 / TKT-9912
    val trxCode: String = "",             // TRX-BMS-202411-994
    val userId: Long,
    val kind: OrderKind,
    val status: OrderStatus = OrderStatus.WAITING_PAYMENT,
    val subtotal: Long,
    val pointsUsed: Int = 0,
    val discount: Long = 0,
    val total: Long,
    val hubId: Long? = null,
    val pickupDeadline: Long? = null,     // batas pengambilan (epoch ms)
    val paymentExpiresAt: Long,           // timer 15 menit
    val createdAt: Long,
    val paidAt: Long? = null,
    val completedAt: Long? = null
)

@Entity(
    tableName = "order_items",
    foreignKeys = [ForeignKey(OrderEntity::class, ["id"], ["orderId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("orderId")]
)
data class OrderItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val productId: Long? = null,
    val ticketPackageId: Long? = null,
    val name: String,                     // snapshot nama
    val subtitle: String = "",            // snapshot UMKM / destinasi
    val unitPrice: Long,
    val qty: Int
)

@Entity(
    tableName = "payments",
    foreignKeys = [ForeignKey(OrderEntity::class, ["id"], ["orderId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("orderId")]
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val method: PaymentMethod,
    val status: PaymentStatus = PaymentStatus.SUCCESS,
    val amount: Long,
    val reference: String,
    val createdAt: Long
)

@Entity(
    tableName = "pickup_passes",
    foreignKeys = [ForeignKey(OrderEntity::class, ["id"], ["orderId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("orderId", unique = true), Index("token", unique = true), Index("backupCode")]
)
data class PickupPassEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val kind: PassKind,
    val lockerCode: String,               // kode loker / "GATE-3"
    val token: String,                    // isi QR (HMAC)
    val backupCode: String,
    val expiresAt: Long,
    val state: PassState = PassState.ACTIVE,
    val redeemedAt: Long? = null
)

// ---------- dompet & poin ----------
@Entity(tableName = "wallet_tx", indices = [Index("userId")])
data class WalletTxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val type: WalletTxType,
    val amount: Long,                     // selalu positif; arah ditentukan type
    val balanceAfter: Long,
    val ref: String = "",
    val createdAt: Long
)

@Entity(tableName = "eco_point_tx", indices = [Index("userId")])
data class EcoPointTxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val delta: Int,
    val reason: String,
    val createdAt: Long
)

// ---------- grup trip & split-bill ----------
@Entity(tableName = "trips", indices = [Index("adminUserId")])
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val adminUserId: Long,
    val startDate: Long? = null,
    val endDate: Long? = null,
    val status: TripStatus = TripStatus.ACTIVE,
    val createdAt: Long
)

@Entity(
    tableName = "trip_members",
    primaryKeys = ["tripId", "userId"],
    foreignKeys = [
        ForeignKey(TripEntity::class, ["id"], ["tripId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(UserEntity::class, ["id"], ["userId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("userId")]
)
data class TripMemberEntity(val tripId: Long, val userId: Long, val role: TripRole, val joinedAt: Long)

@Entity(
    tableName = "pool_contributions",
    foreignKeys = [ForeignKey(TripEntity::class, ["id"], ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")]
)
data class ContributionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val userId: Long,
    val amount: Long,
    val createdAt: Long
)

@Entity(
    tableName = "group_expenses",
    foreignKeys = [ForeignKey(TripEntity::class, ["id"], ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId"), Index("orderId")]
)
data class GroupExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val title: String,
    val category: ExpenseCategory,
    val amount: Long,
    val paidByUserId: Long? = null,       // null = dibayar dari Kas Bersama
    val splitMode: SplitMode = SplitMode.EQUAL,
    val paymentMethod: String? = null,    // "QRIS BCA"
    val note: String = "",
    val receiptPath: String? = null,      // foto struk
    val orderId: Long? = null,            // terisi jika berasal dari pesanan
    val createdAt: Long
)

@Entity(
    tableName = "expense_shares",
    primaryKeys = ["expenseId", "userId"],
    foreignKeys = [ForeignKey(GroupExpenseEntity::class, ["id"], ["expenseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("userId")]
)
data class ExpenseShareEntity(val expenseId: Long, val userId: Long, val amount: Long)

@Entity(
    tableName = "settlements",
    foreignKeys = [ForeignKey(TripEntity::class, ["id"], ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")]
)
data class SettlementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val fromUserId: Long,
    val toUserId: Long,
    val amount: Long,
    val status: SettleStatus = SettleStatus.BELUM_LUNAS,
    val updatedAt: Long
)

// ---------- riwayat Smart Budget ----------
@Entity(
    tableName = "budget_plans",
    foreignKeys = [ForeignKey(UserEntity::class, ["id"], ["userId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("userId")]
)
data class BudgetPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val totalBudget: Long,
    val people: Int,
    val duration: Duration,
    val style: TravelStyle,
    val totalCost: Long,
    val remaining: Long,
    val wisataCost: Long,
    val kulinerCost: Long,
    val umkmCost: Long,
    val summary: String,                  // ringkasan item, satu per baris
    val createdAt: Long
)
