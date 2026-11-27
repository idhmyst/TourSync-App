package putri.data.repo

import kotlinx.coroutines.flow.Flow
import putri.core.budget.*
import putri.data.PutriDatabase
import putri.data.entity.BudgetPlanEntity

data class GeneratedPlan(val plan: BudgetPlan, val savedId: Long?)

/** Smart Budget Planner memakai katalog dari database + filter alergi profil pengguna. */
class BudgetRepository(private val db: PutriDatabase, private val clock: Clock = systemClock) {
    private val plans = db.budgetPlanDao()

    fun history(userId: Long): Flow<List<BudgetPlanEntity>> = plans.observeByUser(userId)

    suspend fun generate(userId: Long, request: BudgetRequest, save: Boolean = true): Result<GeneratedPlan> = runCatching {
        val user = requireNotNull(db.userDao().byId(userId)) { "Akun tidak ditemukan" }
        val allergens = user.allergenList()
        val partners = db.productDao().partners().associateBy { it.id }

        val dests = db.catalogDao().activeDestinations()
            .map { Destination(it.id.toString(), it.name, it.style, it.ticketPrice, it.ecoCertified) }
        val meals = db.catalogDao().activeMeals().map { Meal(it.id.toString(), it.name, it.pricePerPerson) }
        val souvenirs = db.productDao().inStock()
            .filter { p -> p.allergenList().none { it in allergens } }          // "bebas kacang" dll.
            .map { Souvenir(it.id.toString(), it.name, partners[it.partnerId]?.name ?: "UMKM", it.price) }
        require(dests.isNotEmpty() && meals.isNotEmpty() && souvenirs.isNotEmpty()) { "Katalog belum lengkap untuk membuat rekomendasi" }

        val plan = SmartBudgetPlanner(dests, meals, souvenirs).plan(request)
        val id = if (save) plans.insert(BudgetPlanEntity(
            userId = userId, totalBudget = request.totalBudget, people = request.people,
            duration = request.duration, style = request.style, totalCost = plan.totalCost, remaining = plan.remaining,
            wisataCost = plan.wisataCost, kulinerCost = plan.kulinerCost, umkmCost = plan.umkmCost,
            summary = buildString {
                plan.destinations.forEach { appendLine("Wisata: ${it.name}") }
                plan.meals.distinctBy { it.id }.forEach { appendLine("Kuliner: ${it.name} x${plan.meals.count { m -> m.id == it.id }}") }
                plan.souvenirs.forEach { appendLine("Oleh-oleh: ${it.name}") }
            }.trim(),
            createdAt = clock()
        )) else null
        GeneratedPlan(plan, id)
    }

    suspend fun delete(userId: Long, planId: Long): Result<Unit> = runCatching {
        val p = requireNotNull(plans.byId(planId)) { "Rencana tidak ditemukan" }
        require(p.userId == userId) { "Bukan rencana Anda" }
        plans.delete(p)
    }
}
