package putri.core.splitbill

data class Member(val id: String, val name: String)

enum class ExpenseCategory { TRANSPORT, KULINER, TIKET, LOGISTIK }

data class Expense(
    val id: String,
    val title: String,
    val category: ExpenseCategory,
    val amount: Long,
    val paidBy: String,
    /** memberId -> bagian yang ditanggung (rupiah). Jumlahnya harus = amount. */
    val shares: Map<String, Long>
) {
    init {
        require(amount > 0) { "Nominal harus > 0" }
        require(shares.values.sum() == amount) { "Total bagian harus sama dengan nominal" }
    }
}

data class Transfer(val from: String, val to: String, val amount: Long)

object SplitBill {

    /** Bagi rata tanpa kehilangan rupiah: sisa dibagikan 1 per orang dari anggota pertama. */
    fun splitEqually(amount: Long, memberIds: List<String>): Map<String, Long> {
        require(memberIds.isNotEmpty())
        val base = amount / memberIds.size
        val rem = (amount % memberIds.size).toInt()
        return memberIds.mapIndexed { i, id -> id to base + if (i < rem) 1 else 0 }.toMap()
    }

    /** Bagi kustom berdasarkan bobot (mis. 2:1:1). */
    fun splitByWeight(amount: Long, weights: Map<String, Int>): Map<String, Long> {
        require(weights.values.all { it > 0 })
        val total = weights.values.sum()
        val result = weights.mapValues { (_, w) -> amount * w / total }.toMutableMap()
        var rem = amount - result.values.sum()
        for (k in result.keys) { if (rem <= 0) break; result[k] = result.getValue(k) + 1; rem-- }
        return result
    }

    /** Saldo bersih: positif = dia dipiutangi, negatif = dia berutang. */
    fun netBalances(expenses: List<Expense>, memberIds: Collection<String>): Map<String, Long> {
        val bal = memberIds.associateWith { 0L }.toMutableMap()
        for (e in expenses) {
            bal[e.paidBy] = (bal[e.paidBy] ?: 0L) + e.amount
            for ((id, s) in e.shares) bal[id] = (bal[id] ?: 0L) - s
        }
        return bal
    }

    /**
     * Penyederhanaan utang: pertemukan debitur terbesar dengan kreditur terbesar.
     * Hasilnya paling banyak (jumlah orang - 1) transfer.
     */
    fun settle(balances: Map<String, Long>): List<Transfer> {
        val creditors = balances.filter { it.value > 0 }.map { it.key to it.value }.sortedByDescending { it.second }.toMutableList()
        val debtors = balances.filter { it.value < 0 }.map { it.key to -it.value }.sortedByDescending { it.second }.toMutableList()
        val out = mutableListOf<Transfer>()
        var i = 0; var j = 0
        while (i < debtors.size && j < creditors.size) {
            val pay = minOf(debtors[i].second, creditors[j].second)
            out += Transfer(debtors[i].first, creditors[j].first, pay)
            debtors[i] = debtors[i].first to debtors[i].second - pay
            creditors[j] = creditors[j].first to creditors[j].second - pay
            if (debtors[i].second == 0L) i++
            if (creditors[j].second == 0L) j++
        }
        return out
    }
}

/** Kas bersama: target, terpakai, sisa, dan persentase teralokasi. */
data class GroupPool(val target: Long, val spent: Long) {
    val remaining get() = target - spent
    val allocatedPercent get() = if (target == 0L) 0.0 else Math.round(spent * 1000.0 / target) / 10.0
}
