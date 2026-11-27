package putri.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import putri.app.*
import putri.app.ui.*
import putri.data.entity.*
import putri.data.repo.*

import kotlin.math.roundToInt
import putri.core.budget.BudgetPlan
import putri.core.budget.BudgetRequest
import putri.core.budget.Duration
import putri.core.budget.TravelStyle

// ================================================================ SMART BUDGET
@Composable
fun BudgetScreen() {
    val env = LocalApp.current
    if (!env.session.loggedIn) { LoginPrompt("Masuk untuk memakai Smart Budget Planner"); return }
    var budget by remember { mutableFloatStateOf(500_000f) }
    var people by remember { mutableIntStateOf(2) }
    var duration by remember { mutableStateOf(Duration.FULL_DAY) }
    var style by remember { mutableStateOf(TravelStyle.ALAM_RELAKSASI) }
    var plan by remember { mutableStateOf<BudgetPlan?>(null) }
    var busy by remember { mutableStateOf(false) }
    val history by remember { env.c.budget.history(env.uid) }.collectAsState(emptyList())

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        HeroHeader("Smart Budget Planner", "Rencana liburan otomatis sesuai anggaran", onBack = { env.nav.popBackStack() })
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PCard {
                Text("Total anggaran", color = Muted, fontSize = 13.sp)
                Text(rupiah(budget.toLong()), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Pine)
                Slider(
                    value = budget, onValueChange = { budget = (it / 25_000f).roundToInt() * 25_000f },
                    valueRange = 100_000f..2_000_000f
                )
                Row { Text("Rp100rb", color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f)); Text("Rp2jt", color = Muted, fontSize = 11.sp) }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Jumlah orang", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Stepper(people, { people = it }, min = 1, max = 10)
                }
            }
            PCard {
                Text("Durasi", fontWeight = FontWeight.SemiBold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Duration.values().forEach { d -> Choice(d.label, duration == d, { duration = d }) }
                }
                Spacer(Modifier.height(8.dp))
                Text("Gaya liburan", fontWeight = FontWeight.SemiBold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TravelStyle.values().forEach { s -> Choice(s.label, style == s, { style = s }) }
                }
            }
            PButton(if (busy) "Menghitung…" else "✨ Buat Rekomendasi", {
                busy = true
                env.scope.launch {
                    env.c.budget.generate(env.uid, BudgetRequest(budget.toLong(), people, duration, style)).ui(env) { plan = it.plan }
                    busy = false
                }
            }, Modifier.fillMaxWidth(), accent = true, enabled = !busy)

            plan?.let { PlanResult(it) }

            if (history.isNotEmpty()) {
                SectionTitle("Riwayat Rencana")
                history.forEach { h ->
                    PCard(Modifier.padding(bottom = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${rupiah(h.totalBudget)} • ${h.people} orang", fontWeight = FontWeight.Bold)
                                Text("${h.duration.label} • ${h.style.label}", color = Muted, fontSize = 12.sp)
                                Text("Terpakai ${rupiah(h.totalCost)} • sisa ${rupiah(h.remaining)}", fontSize = 12.sp)
                            }
                            IconButton(onClick = { env.scope.launch { env.c.budget.delete(env.uid, h.id).ui(env, "Rencana dihapus") } }) {
                                Icon(Icons.Filled.Delete, "Hapus", tint = Danger)
                            }
                        }
                        Text(h.summary, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PlanResult(plan: BudgetPlan) {
    val env = LocalApp.current
    val people = plan.request.people
    PCard(color = Mint) {
        Text("Rekomendasi Paket Kamu", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = PineDark)
        Spacer(Modifier.height(6.dp))
        LabelValue("Estimasi total", rupiah(plan.totalCost), bold = true)
        LabelValue("Sisa disimpan ke saldo", rupiah(plan.remaining))
        Spacer(Modifier.height(8.dp))
        listOf(
            Triple("🌲 Wisata", plan.wisataCost, plan.wisataPercent), Triple("🥘 Kuliner", plan.kulinerCost, plan.kulinerPercent),
            Triple("🎁 Oleh-oleh UMKM", plan.umkmCost, plan.umkmPercent)
        ).forEach { (label, cost, pct) ->
            Row { Text(label, modifier = Modifier.weight(1f), fontSize = 13.sp); Text("${rupiah(cost)} • $pct%", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            LinearProgressIndicator(progress = { pct / 100f }, Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).padding(bottom = 0.dp), color = Pine, trackColor = Color.White)
            Spacer(Modifier.height(6.dp))
        }
    }
    if (plan.destinations.isNotEmpty()) {
        Text("Destinasi", fontWeight = FontWeight.Bold)
        plan.destinations.forEach { d ->
            PCard(onClick = { env.nav.navigate("destination/${d.id}") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(styleEmoji(d.style), fontSize = 24.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) { Text(d.name, fontWeight = FontWeight.SemiBold); Text("${rupiah(d.ticketPerPerson)} × $people orang", color = Muted, fontSize = 12.sp) }
                    Text("Pesan →", color = Pine, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    if (plan.meals.isNotEmpty()) {
        Text("Kuliner", fontWeight = FontWeight.Bold)
        PCard {
            plan.meals.groupBy { it.id }.values.forEach { g ->
                LabelValue("${g.first().name} ×${g.size}", rupiah(g.first().pricePerPerson * people * g.size))
            }
        }
    }
    if (plan.souvenirs.isNotEmpty()) {
        Text("Oleh-oleh UMKM", fontWeight = FontWeight.Bold)
        PCard {
            plan.souvenirs.forEach { LabelValue(it.name, rupiah(it.price)) }
            Text("Min. 10% anggaran untuk UMKM lokal 💚", color = Muted, fontSize = 12.sp)
        }
    }
}

// ================================================================ DETAIL DESTINASI
@Composable
fun DestinationScreen(id: Long) {
    val env = LocalApp.current
    val dest by produceState<DestinationEntity?>(null, id) { value = env.c.catalog.destination(id) }
    val tickets by remember(id) { env.c.catalog.tickets(id) }.collectAsState(emptyList())
    var selected by remember { mutableStateOf<Long?>(null) }
    var qty by remember { mutableIntStateOf(1) }
    var day by remember { mutableIntStateOf(0) }
    val now = rememberNow()
    val d = dest
    if (d == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return }
    val sel = tickets.firstOrNull { it.id == selected } ?: tickets.firstOrNull()
    val todayOpen = timeAt(d.closeTime, 0) > now
    if (!todayOpen && day == 0) day = 1

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background, contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (sel != null) Column(Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Total", color = Muted, fontSize = 12.sp); Text(rupiah(sel.price * qty), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Pine) }
                    PButton("Pesan Pass", {
                        if (!env.session.loggedIn) { env.toast("Masuk dulu untuk memesan"); env.nav.navigate("login"); return@PButton }
                        env.scope.launch {
                            env.c.orders.createTicketOrder(env.uid, sel.id, qty, timeAt(d.closeTime, day)).ui(env) { env.nav.navigate("checkout/${it.id}") }
                        }
                    }, accent = true)
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
            HeroHeader(d.name, d.area, onBack = { env.nav.popBackStack() }) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                    if (d.ecoCertified) Pill("ECO-CERTIFIED", bg = Sun, fg = Ink)
                    if (d.badge.isNotBlank() && d.badge != "Eco-Certified") Pill(d.badge.uppercase())
                    if (d.rating > 0) Pill("⭐ ${d.rating} (${d.reviewCount})", bg = Color.White, fg = Ink)
                }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoTile("🕗", "Jam Buka", "${d.openTime}–${d.closeTime}", Modifier.weight(1f))
                    d.altitudeMdpl?.let { InfoTile("⛰️", "Ketinggian", "$it mdpl", Modifier.weight(1f)) }
                    d.avgTempC?.let { InfoTile("🌡️", "Suhu", "$it°C", Modifier.weight(1f)) }
                }
                PCard {
                    Text("Tentang", fontWeight = FontWeight.Bold)
                    Text(d.description, color = Muted)
                    Text("📍 ${d.address}", fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
                val hl = d.highlights.split(",").filter { it.isNotBlank() }
                if (hl.isNotEmpty()) PCard {
                    Text("Daya Tarik", fontWeight = FontWeight.Bold)
                    hl.forEach { Text("• ${it.trim()}", color = Muted) }
                }
                val fac = d.facilities.split(",").filter { it.isNotBlank() }
                if (fac.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { fac.forEach { Pill(it.trim()) } }

                Text("Pilih Tiket", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                if (tickets.isEmpty()) EmptyState("🎫", "Belum ada paket tiket")
                tickets.forEach { t ->
                    val isSel = sel?.id == t.id
                    PCard(onClick = { selected = t.id }, color = if (isSel) Mint else Color.White) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = isSel, onClick = { selected = t.id })
                            Column(Modifier.weight(1f)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(t.name, fontWeight = FontWeight.SemiBold); if (t.popular) Pill("POPULER", bg = Sun, fg = Ink)
                                }
                                if (t.description.isNotBlank()) Text(t.description, color = Muted, fontSize = 12.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) { Text(rupiah(t.price), fontWeight = FontWeight.ExtraBold, color = Pine); Text("/${t.unitLabel}", color = Muted, fontSize = 11.sp) }
                        }
                    }
                }
                if (sel != null) {
                    PCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Jumlah", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Stepper(qty, { qty = it }, min = 1, max = 20)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Tanggal kunjungan", fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Choice("Hari ini", day == 0, { day = 0 }, enabled = todayOpen)
                            Choice("Besok", day == 1, { day = 1 })
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun InfoTile(emoji: String, label: String, value: String, modifier: Modifier) {
    Column(modifier.background(Color.White, RoundedCornerShape(14.dp)).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 20.sp); Text(label, color = Muted, fontSize = 11.sp); Text(value, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}
