package putri.core.budget

/**
 * Rekomendasi paket wisata berbasis anggaran (rule-based, bukan AI sungguhan).
 *
 * Langkah:
 * 1. Bagi anggaran ke 3 kategori (default 40/35/25, UMKM min 10%).
 * 2. Wisata: pilih destinasi sesuai gaya dulu, lalu yang lain, selama muat di jatah.
 * 3. Kuliner: pilih menu termahal yang masih muat untuk semua jatah makan.
 * 4. UMKM: 0/1 knapsack, belanjakan sedekat mungkin ke jatah (tanpa melebihi).
 * Sisa uang tidak dipaksa habis, jadi jadi "sisa disimpan ke saldo".
 */
class SmartBudgetPlanner(
    private val destinations: List<Destination> = DummyCatalog.destinations,
    private val meals: List<Meal> = DummyCatalog.meals,
    private val souvenirs: List<Souvenir> = DummyCatalog.souvenirs,
    private val share: CategoryShare = CategoryShare()
) {
    fun plan(req: BudgetRequest): BudgetPlan {
        val wisataBudget = (req.totalBudget * share.wisata).toLong()
        val kulinerBudget = (req.totalBudget * share.kuliner).toLong()
        val umkmBudget = req.totalBudget - wisataBudget - kulinerBudget

        val (dest, wisataCost) = pickDestinations(req, wisataBudget)
        val (meal, kulinerCost) = pickMeals(req, kulinerBudget)
        val (souv, umkmCost) = pickSouvenirs(umkmBudget)
        return BudgetPlan(req, dest, meal, souv, wisataCost, kulinerCost, umkmCost)
    }

    private fun pickDestinations(req: BudgetRequest, budget: Long): Pair<List<Destination>, Long> {
        // gaya pilihan dulu, di dalamnya yang termurah dulu agar muat banyak
        val ordered = destinations.sortedWith(
            compareBy<Destination> { it.style != req.style }.thenBy { it.ticketPerPerson }
        )
        val chosen = mutableListOf<Destination>()
        var spent = 0L
        for (d in ordered) {
            if (chosen.size >= req.duration.maxDestinations) break
            val cost = d.ticketPerPerson * req.people
            if (spent + cost <= budget) { chosen += d; spent += cost }
        }
        return chosen to spent
    }

    private fun pickMeals(req: BudgetRequest, budget: Long): Pair<List<Meal>, Long> {
        val slots = req.duration.meals
        val perMealPerPerson = budget / (slots * req.people)
        val best = meals.filter { it.pricePerPerson <= perMealPerPerson }.maxByOrNull { it.pricePerPerson }
            ?: return emptyList<Meal>() to 0L
        return List(slots) { best } to best.pricePerPerson * req.people * slots
    }

    private fun pickSouvenirs(budget: Long): Pair<List<Souvenir>, Long> {
        val unit = 1_000L
        val cap = (budget / unit).toInt()
        val n = souvenirs.size
        val dp = Array(n + 1) { IntArray(cap + 1) }
        for (i in 1..n) {
            val w = (souvenirs[i - 1].price / unit).toInt()
            for (c in 0..cap) {
                dp[i][c] = dp[i - 1][c]
                if (w <= c) dp[i][c] = maxOf(dp[i][c], dp[i - 1][c - w] + w)
            }
        }
        val chosen = mutableListOf<Souvenir>()
        var c = cap
        for (i in n downTo 1) {
            if (dp[i][c] != dp[i - 1][c]) {
                chosen += souvenirs[i - 1]
                c -= (souvenirs[i - 1].price / unit).toInt()
            }
        }
        return chosen.reversed() to chosen.sumOf { it.price }
    }
}
