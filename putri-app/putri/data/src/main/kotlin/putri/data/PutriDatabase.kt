package putri.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import putri.data.dao.*
import putri.data.entity.*

@Database(
    entities = [
        UserEntity::class, DestinationEntity::class, TicketPackageEntity::class, MealEntity::class,
        PartnerEntity::class, ProductEntity::class, HubEntity::class, LockerEntity::class,
        CartItemEntity::class, OrderEntity::class, OrderItemEntity::class, PaymentEntity::class,
        PickupPassEntity::class, WalletTxEntity::class, EcoPointTxEntity::class,
        TripEntity::class, TripMemberEntity::class, ContributionEntity::class,
        GroupExpenseEntity::class, ExpenseShareEntity::class, SettlementEntity::class,
        BudgetPlanEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class PutriDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun catalogDao(): CatalogDao
    abstract fun productDao(): ProductDao
    abstract fun cartDao(): CartDao
    abstract fun hubDao(): HubDao
    abstract fun orderDao(): OrderDao
    abstract fun walletDao(): WalletDao
    abstract fun tripDao(): TripDao
    abstract fun budgetPlanDao(): BudgetPlanDao

    companion object {
        fun build(context: Context): PutriDatabase =
            Room.databaseBuilder(context.applicationContext, PutriDatabase::class.java, "putri.db").build()

        fun inMemory(context: Context): PutriDatabase =
            Room.inMemoryDatabaseBuilder(context.applicationContext, PutriDatabase::class.java).build()
    }
}
