package putri.core.budget

enum class Duration(val label: String, val maxDestinations: Int, val meals: Int) {
    HALF_DAY("Half Day (4 jam)", 1, 1),
    FULL_DAY("Full Day", 3, 2),
    TWO_DAYS_ONE_NIGHT("2H 1M Menginap", 5, 5)
}

enum class TravelStyle(val label: String) {
    ALAM_RELAKSASI("Alam & Relaksasi"),
    PETUALANGAN("Petualangan Seru"),
    EDUKASI_KULINER("Edukasi & Kuliner Keluarga")
}

data class Destination(val id: String, val name: String, val style: TravelStyle, val ticketPerPerson: Long, val ecoCertified: Boolean = true)
data class Meal(val id: String, val name: String, val pricePerPerson: Long)
data class Souvenir(val id: String, val name: String, val umkm: String, val price: Long)

data class BudgetRequest(
    val totalBudget: Long,
    val people: Int,
    val duration: Duration,
    val style: TravelStyle
) {
    init {
        require(totalBudget > 0) { "Anggaran harus > 0" }
        require(people in 1..20) { "Jumlah orang tidak valid" }
    }
}

data class CategoryShare(val wisata: Double = 0.40, val kuliner: Double = 0.35, val umkm: Double = 0.25) {
    init {
        require(kotlin.math.abs(wisata + kuliner + umkm - 1.0) < 1e-9) { "Total persentase harus 100%" }
        require(umkm >= MIN_UMKM) { "Porsi UMKM minimal 10%" }
    }
    companion object { const val MIN_UMKM = 0.10 }
}

data class BudgetPlan(
    val request: BudgetRequest,
    val destinations: List<Destination>,
    val meals: List<Meal>,
    val souvenirs: List<Souvenir>,
    val wisataCost: Long,
    val kulinerCost: Long,
    val umkmCost: Long
) {
    val totalCost get() = wisataCost + kulinerCost + umkmCost
    val remaining get() = request.totalBudget - totalCost
    private fun pct(x: Long) = if (totalCost == 0L) 0 else Math.round(x * 100.0 / totalCost).toInt()
    val wisataPercent get() = pct(wisataCost)
    val kulinerPercent get() = pct(kulinerCost)
    val umkmPercent get() = pct(umkmCost)
}
