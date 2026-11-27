package putri.core.payment

/** Asumsi dari desain: 120 poin = hemat Rp5.000 -> 24 poin per Rp1.000. Bisa diubah. */
object EcoPoints {
    const val POINTS_PER_1000 = 24
    const val EARN_PER_RUPIAH = 10_000L // 1 poin tiap belanja Rp10.000 (asumsi)

    fun discountFor(points: Int): Long = points / POINTS_PER_1000 * 1_000L

    fun pointsToRedeem(available: Int, bill: Long): Int {
        val maxByBill = (bill / 1_000L * POINTS_PER_1000).toInt()
        val usable = minOf(available, maxByBill)
        return usable / POINTS_PER_1000 * POINTS_PER_1000
    }

    fun earned(paid: Long): Int = (paid / EARN_PER_RUPIAH).toInt()
}

data class Checkout(val subtotal: Long, val pointsUsed: Int, val discount: Long, val total: Long, val pointsEarned: Int)

object PaymentCalculator {
    fun checkout(subtotal: Long, availablePoints: Int, usePoints: Boolean): Checkout {
        val used = if (usePoints) EcoPoints.pointsToRedeem(availablePoints, subtotal) else 0
        val disc = EcoPoints.discountFor(used)
        val total = subtotal - disc
        return Checkout(subtotal, used, disc, total, EcoPoints.earned(total))
    }

    fun canPay(balance: Long, total: Long) = balance >= total
}
