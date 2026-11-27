package putri.core

import putri.core.budget.*
import putri.core.payment.*
import putri.core.pickup.*
import putri.core.splitbill.*
import kotlin.test.*

class CoreTest {

    // ---- Smart Budget ----
    @Test fun planNeverExceedsBudget() {
        val planner = SmartBudgetPlanner()
        for (b in listOf(20_000L, 100_000L, 500_000L, 1_500_000L))
            for (d in Duration.values()) for (s in TravelStyle.values()) {
                val p = planner.plan(BudgetRequest(b, 2, d, s))
                assertTrue(p.totalCost <= b, "budget=$b $d $s")
                assertTrue(p.remaining >= 0)
            }
    }

    @Test fun umkmGetsAtLeastTenPercent() {
        val p = SmartBudgetPlanner().plan(BudgetRequest(500_000, 2, Duration.FULL_DAY, TravelStyle.ALAM_RELAKSASI))
        assertTrue(p.umkmCost >= 50_000, "UMKM = ${p.umkmCost}")
    }

    @Test fun styleIsPrioritised() {
        val p = SmartBudgetPlanner().plan(BudgetRequest(500_000, 1, Duration.HALF_DAY, TravelStyle.PETUALANGAN))
        assertEquals(TravelStyle.PETUALANGAN, p.destinations.first().style)
    }

    @Test fun umkmShareBelowTenPercentRejected() {
        assertFailsWith<IllegalArgumentException> { CategoryShare(0.5, 0.45, 0.05) }
    }

    // ---- Split bill ----
    @Test fun equalSplitKeepsEveryRupiah() {
        val s = SplitBill.splitEqually(450_000, listOf("a", "b", "c", "d"))
        assertEquals(112_500, s.getValue("a"))
        val odd = SplitBill.splitEqually(100_000, listOf("a", "b", "c"))
        assertEquals(100_000, odd.values.sum())
    }

    @Test fun settleMinimisesTransfers() {
        val ids = listOf("rian", "dimas", "sarah", "nabila")
        val e1 = Expense("1", "Jeep", ExpenseCategory.TRANSPORT, 400_000, "dimas", SplitBill.splitEqually(400_000, ids))
        val e2 = Expense("2", "Tiket", ExpenseCategory.TIKET, 200_000, "sarah", SplitBill.splitEqually(200_000, ids))
        val bal = SplitBill.netBalances(listOf(e1, e2), ids)
        assertEquals(0L, bal.values.sum())
        val tf = SplitBill.settle(bal)
        assertTrue(tf.size <= ids.size - 1)
        // setelah transfer semua saldo harus nol
        val after = bal.toMutableMap()
        tf.forEach { after[it.from] = after.getValue(it.from) + it.amount; after[it.to] = after.getValue(it.to) - it.amount }
        assertTrue(after.values.all { it == 0L })
    }

    @Test fun poolNumbersMatchDesign() {
        val pool = GroupPool(1_500_000, 1_250_000)
        assertEquals(250_000, pool.remaining)
        assertEquals(83.3, pool.allocatedPercent)
    }

    // ---- Pickup ----
    private val svc = PickupPassService("rahasia".toByteArray())

    @Test fun validPassIsReadyThenUsed() {
        val pass = svc.issue("TOUR-BMS-88291", "LOKER-08", 1_000_000)
        assertEquals(PassStatus.READY, svc.validate(pass.token, "LOKER-08", 500_000))
        assertEquals(PassStatus.READY, svc.redeem(pass.token, "LOKER-08", 500_000))
        assertEquals(PassStatus.USED, svc.validate(pass.token, "LOKER-08", 600_000))
    }

    @Test fun expiredWrongLockerAndTamperedAreRejected() {
        val pass = svc.issue("ORD1", "LOKER-08", 1_000)
        assertEquals(PassStatus.EXPIRED, svc.validate(pass.token, "LOKER-08", 2_000))
        assertEquals(PassStatus.WRONG_LOCKER, svc.validate(pass.token, "LOKER-09", 500))
        val forged = pass.token.replace("|1000|", "|9999999|")
        assertEquals(PassStatus.INVALID, svc.validate(forged, "LOKER-08", 500))
        assertEquals(PassStatus.INVALID, svc.validate("sampah", "LOKER-08", 500))
    }

    @Test fun countdownFormat() {
        assertEquals("03:45:09", formatCountdown((3 * 3600 + 45 * 60 + 9) * 1000L))
    }

    // ---- Payment ----
    @Test fun ecoPointsMatchDesign() {
        assertEquals(5_000L, EcoPoints.discountFor(120))
        val c = PaymentCalculator.checkout(147_000, 320, true)
        assertEquals(147_000 - c.discount, c.total)
        assertTrue(c.pointsUsed <= 320)
    }

    @Test fun pointsNeverExceedBill() {
        val c = PaymentCalculator.checkout(3_000, 10_000, true)
        assertTrue(c.total >= 0)
    }
}
