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

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import putri.core.splitbill.ExpenseCategory

private fun catEmoji(c: ExpenseCategory) = when (c) {
    ExpenseCategory.TRANSPORT -> "🚙"; ExpenseCategory.KULINER -> "🍜"; ExpenseCategory.TIKET -> "🎫"; ExpenseCategory.LOGISTIK -> "🎒"
}
private fun catLabel(c: ExpenseCategory) = when (c) {
    ExpenseCategory.TRANSPORT -> "Transport"; ExpenseCategory.KULINER -> "Kuliner"; ExpenseCategory.TIKET -> "Tiket"; ExpenseCategory.LOGISTIK -> "Logistik"
}

// ================================================================ DAFTAR GRUP (tab)
@Composable
fun TripListTab() {
    val env = LocalApp.current
    val active by remember { env.c.trips.myTrips(env.uid) }.collectAsState(emptyList())
    val archived by remember { env.c.trips.myTrips(env.uid, TripStatus.ARCHIVED) }.collectAsState(emptyList())
    var create by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }

    if (create) FormDialog("Buat Grup Trip", { create = false }, "Buat", confirmEnabled = name.isNotBlank(), onConfirm = {
        env.scope.launch {
            env.c.trips.createTrip(env.uid, name, desc).ui(env, "Grup dibuat") { t -> create = false; name = ""; desc = ""; env.nav.navigate("trip/${t.id}") }
        }
    }) {
        Field("Nama grup (mis. Trip Baturraden Squad)", name, { name = it })
        Field("Deskripsi (opsional)", desc, { desc = it })
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            HeroHeader("Group Trip & Split-Bill", "Kas bersama dan patungan tanpa ribet")
            LazyColumn(contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (active.isEmpty()) item { EmptyState("🧑‍🤝‍🧑", "Belum ada grup.\nBuat grup lalu undang temanmu lewat username/email/no HP.") }
                items(active, key = { it.id }) { t -> TripCard(t) }
                if (archived.isNotEmpty()) {
                    item { Text("Arsip", fontWeight = FontWeight.Bold, color = Muted, modifier = Modifier.padding(top = 8.dp)) }
                    items(archived, key = { it.id }) { t -> TripCard(t) }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { create = true }, containerColor = Sun, contentColor = Ink,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Buat Grup", fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun TripCard(t: TripEntity) {
    val env = LocalApp.current
    val members by remember(t.id) { env.c.trips.members(t.id) }.collectAsState(emptyList())
    val pool by remember(t.id) { env.c.trips.pool(t.id) }.collectAsState(PoolStatus(0, 0))
    PCard(onClick = { env.nav.navigate("trip/${t.id}") }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).background(Mint, CircleShape), contentAlignment = Alignment.Center) { Text("🧳", fontSize = 24.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.name, fontWeight = FontWeight.Bold)
                Text("${members.size} anggota • kas ${rupiah(pool.collected)}", color = Muted, fontSize = 12.sp)
            }
            if (t.status == TripStatus.ARCHIVED) Pill("ARSIP", bg = Color(0xFFEDEDED), fg = Muted) else Text("Sisa ${rupiah(pool.remaining)}", fontWeight = FontWeight.Bold, color = Pine, fontSize = 13.sp)
        }
    }
}

// ================================================================ DETAIL GRUP
@Composable
fun TripScreen(tripId: Long) {
    val env = LocalApp.current
    val trip by remember(tripId) { env.c.trips.trip(tripId) }.collectAsState(null)
    val members by remember(tripId) { env.c.trips.members(tripId) }.collectAsState(emptyList())
    val expenses by remember(tripId) { env.c.trips.expenses(tripId) }.collectAsState(emptyList())
    val settlements by remember(tripId) { env.c.trips.settlements(tripId) }.collectAsState(emptyList())
    val pool by remember(tripId) { env.c.trips.pool(tripId) }.collectAsState(PoolStatus(0, 0))
    var dlg by remember { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var deleteExpense by remember { mutableStateOf<GroupExpenseEntity?>(null) }

    val t = trip
    if (t == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Grup tidak ditemukan", color = Muted) }; return }
    val names = members.associate { it.userId to it.fullName }
    val isAdmin = t.adminUserId == env.uid
    val active = t.status == TripStatus.ACTIVE
    fun nm(id: Long?) = if (id == null) "Kas Bersama" else (names[id] ?: "Anggota")

    fun close() { dlg = null; text = "" }
    when (dlg) {
        "contribute" -> FormDialog("Isi Kas Bersama", { close() }, "Isi Kas", confirmEnabled = (text.toLongOrNull() ?: 0) > 0, onConfirm = {
            env.scope.launch { env.c.trips.contribute(env.uid, tripId, text.toLongOrNull() ?: 0).ui(env, "Kas bertambah") { close() } }
        }) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(50_000L, 100_000L, 250_000L, 500_000L).forEach { v -> Choice(rupiah(v), text == v.toString(), { text = v.toString() }) }
            }
            Field("Nominal", text, { text = it.filter(Char::isDigit) }, keyboard = KeyboardType.Number)
            Text("Dipotong dari Eco-Wallet kamu.", color = Muted, fontSize = 12.sp)
        }
        "invite" -> FormDialog("Undang Anggota", { close() }, "Undang", confirmEnabled = text.isNotBlank(), onConfirm = {
            env.scope.launch { env.c.trips.invite(env.uid, tripId, text).ui(env, "Anggota ditambahkan") { close() } }
        }) {
            Field("Username / email / no HP", text, { text = it })
            Text("Teman harus sudah punya akun Wisatawan.", color = Muted, fontSize = 12.sp)
        }
        "rename" -> FormDialog("Ubah Nama Grup", { close() }, confirmEnabled = text.isNotBlank(), onConfirm = {
            env.scope.launch { env.c.trips.updateTrip(env.uid, tripId, text, t.description, t.startDate, t.endDate).ui(env, "Tersimpan") { close() } }
        }) { Field("Nama grup", text, { text = it }) }
        "archive" -> ConfirmDialog("Arsipkan grup?", "Hanya bisa jika semua utang sudah lunas.", "Arsipkan", { close() }) {
            env.scope.launch { env.c.trips.archive(env.uid, tripId).ui(env, "Grup diarsipkan") { env.nav.popBackStack() } }
        }
        "delete" -> ConfirmDialog("Hapus grup?", "Semua catatan pengeluaran ikut terhapus permanen.", "Hapus", { close() }) {
            env.scope.launch { env.c.trips.deleteTrip(env.uid, tripId).ui(env, "Grup dihapus") { env.nav.popBackStack() } }
        }
    }
    deleteExpense?.let { e ->
        ConfirmDialog("Hapus pengeluaran?", e.title, "Hapus", { deleteExpense = null }) {
            env.scope.launch { env.c.trips.deleteExpense(env.uid, e.id).ui(env, "Dihapus") }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            HeroHeader(t.name, if (t.description.isBlank()) "${members.size} anggota" else t.description, onBack = { env.nav.popBackStack() }, actions = {
                if (isAdmin) Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Menu", tint = Color.White) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Ubah nama") }, onClick = { menu = false; text = t.name; dlg = "rename" })
                        if (active) DropdownMenuItem(text = { Text("Arsipkan") }, onClick = { menu = false; dlg = "archive" })
                        DropdownMenuItem(text = { Text("Hapus grup", color = Danger) }, onClick = { menu = false; dlg = "delete" })
                    }
                }
            }) {
                Column(Modifier.fillMaxWidth().padding(top = 8.dp).background(Color.White.copy(alpha = .15f), RoundedCornerShape(18.dp)).padding(14.dp)) {
                    Text("Dompet Bersama", color = Color.White.copy(alpha = .8f), fontSize = 12.sp)
                    Text(rupiah(pool.remaining), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp)
                    Text("Terkumpul ${rupiah(pool.collected)} • terpakai ${rupiah(pool.spent)}", color = Color.White.copy(alpha = .85f), fontSize = 12.sp)
                    LinearProgressIndicator(
                        progress = { (pool.view.allocatedPercent / 100.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(8.dp).clip(RoundedCornerShape(50)),
                        color = Sun, trackColor = Color.White.copy(alpha = .3f)
                    )
                    Text("${pool.view.allocatedPercent}% sudah dialokasikan", color = Color.White.copy(alpha = .85f), fontSize = 11.sp)
                    if (active) Button(onClick = { text = ""; dlg = "contribute" }, colors = ButtonDefaults.buttonColors(containerColor = Sun, contentColor = Ink), modifier = Modifier.padding(top = 6.dp)) { Text("+ Isi Kas", fontWeight = FontWeight.Bold) }
                }
            }

            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // ---- anggota
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Anggota (${members.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    if (isAdmin && active) TextButton(onClick = { text = ""; dlg = "invite" }) { Text("+ Undang") }
                }
                PCard {
                    members.forEach { m ->
                        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(34.dp).background(Mint, CircleShape), contentAlignment = Alignment.Center) { Text(initials(m.fullName), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = PineDark) }
                            Spacer(Modifier.width(10.dp))
                            Text(m.fullName + if (m.userId == env.uid) " (kamu)" else "", modifier = Modifier.weight(1f))
                            if (m.role == TripRole.ADMIN) Pill("ADMIN")
                            else if (active && (isAdmin || m.userId == env.uid)) TextButton(onClick = {
                                env.scope.launch { env.c.trips.removeMember(env.uid, tripId, m.userId).ui(env, if (m.userId == env.uid) "Kamu keluar dari grup" else "Anggota dikeluarkan") { if (m.userId == env.uid) env.nav.popBackStack() } }
                            }) { Text(if (m.userId == env.uid) "Keluar" else "Keluarkan", color = Danger, fontSize = 12.sp) }
                        }
                    }
                }

                // ---- status split-bill
                Text("Status Split-Bill", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if (settlements.isEmpty()) PCard(color = Mint) { Text(if (expenses.isEmpty()) "Belum ada pengeluaran." else "Semua impas, tidak ada utang 🎉", color = PineDark) }
                settlements.forEach { s ->
                    PCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${nm(s.fromUserId)} → ${nm(s.toUserId)}", fontWeight = FontWeight.SemiBold)
                                Text(rupiah(s.amount), color = Pine, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                            }
                            when (s.status) {
                                SettleStatus.LUNAS -> Pill("LUNAS", bg = OkGreen, fg = Color.White)
                                SettleStatus.KONFIRMASI -> Pill("MENUNGGU KONFIRMASI", bg = Warn, fg = Color.White)
                                SettleStatus.BELUM_LUNAS -> Pill("BELUM LUNAS", bg = Danger, fg = Color.White)
                            }
                        }
                        if (s.fromUserId == env.uid && s.status == SettleStatus.BELUM_LUNAS) Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { env.scope.launch { env.c.trips.payViaWallet(env.uid, s.id).ui(env, "Lunas via Eco-Wallet") } }, shape = RoundedCornerShape(12.dp)) { Text("Bayar via Wallet", fontSize = 12.sp) }
                            OutlinedButton(onClick = { env.scope.launch { env.c.trips.markPaid(env.uid, s.id).ui(env, "Menunggu konfirmasi penerima") } }, shape = RoundedCornerShape(12.dp)) { Text("Sudah Bayar", fontSize = 12.sp) }
                        }
                        if (s.toUserId == env.uid && s.status == SettleStatus.KONFIRMASI) Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { env.scope.launch { env.c.trips.confirm(env.uid, s.id).ui(env, "Dikonfirmasi lunas") } }, shape = RoundedCornerShape(12.dp)) { Text("Konfirmasi Terima", fontSize = 12.sp) }
                            OutlinedButton(onClick = { env.scope.launch { env.c.trips.reject(env.uid, s.id).ui(env, "Dikembalikan ke belum lunas") } }, shape = RoundedCornerShape(12.dp)) { Text("Belum Masuk", fontSize = 12.sp) }
                        }
                    }
                }

                // ---- riwayat pengeluaran
                Text("Riwayat Transaksi", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp))
                if (expenses.isEmpty()) EmptyState("🧾", "Belum ada pengeluaran.\nKetuk “Catat Baru” untuk mulai.")
                expenses.forEach { e ->
                    val canEdit = e.orderId == null && active && (isAdmin || e.paidByUserId == env.uid)
                    PCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(catEmoji(e.category), fontSize = 26.sp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(e.title, fontWeight = FontWeight.Bold)
                                Text("${catLabel(e.category)} • dibayar ${nm(e.paidByUserId)} • ${if (e.splitMode == SplitMode.EQUAL) "bagi rata" else "kustom"}", color = Muted, fontSize = 11.sp)
                                Text(fmtDate(e.createdAt), color = Muted, fontSize = 11.sp)
                                if (e.receiptPath != null) Text("📎 struk terlampir", color = Pine, fontSize = 11.sp)
                            }
                            Text(rupiah(e.amount), fontWeight = FontWeight.ExtraBold, color = Pine)
                        }
                        if (canEdit) Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { env.nav.navigate("expense/$tripId/${e.id}") }) { Text("Ubah") }
                            TextButton(onClick = { deleteExpense = e }) { Text("Hapus", color = Danger) }
                        }
                    }
                }
                Spacer(Modifier.height(80.dp))
            }
        }
        if (active) ExtendedFloatingActionButton(
            onClick = { env.nav.navigate("expense/$tripId/0") }, containerColor = Sun, contentColor = Ink,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Catat Baru", fontWeight = FontWeight.Bold) }
    }
}

// ================================================================ TAMBAH / UBAH PENGELUARAN
@Composable
fun ExpenseScreen(tripId: Long, expenseId: Long) {
    val env = LocalApp.current
    val members by remember(tripId) { env.c.trips.members(tripId) }.collectAsState(emptyList())
    val pool by remember(tripId) { env.c.trips.pool(tripId) }.collectAsState(PoolStatus(0, 0))
    val editing = expenseId > 0
    val existing by produceState<GroupExpenseEntity?>(null, expenseId) { if (editing) value = env.c.database.tripDao().expense(expenseId) }
    val existingShares by produceState<List<ExpenseShareEntity>>(emptyList(), expenseId) { if (editing) value = env.c.trips.shares(expenseId) }

    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(ExpenseCategory.KULINER) }
    var amountText by remember { mutableStateOf("") }
    var paidBy by remember { mutableStateOf<Long?>(env.uid) }
    var mode by remember { mutableStateOf(SplitMode.EQUAL) }
    var participants by remember { mutableStateOf(setOf<Long>()) }
    var custom by remember { mutableStateOf(mapOf<Long, String>()) }
    var note by remember { mutableStateOf("") }
    var receipt by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(!editing) }

    LaunchedEffect(members, existing, existingShares) {
        if (members.isEmpty()) return@LaunchedEffect
        if (editing) {
            val e = existing ?: return@LaunchedEffect
            if (loaded) return@LaunchedEffect
            title = e.title; category = e.category; amountText = e.amount.toString(); paidBy = e.paidByUserId; mode = e.splitMode
            note = e.note; receipt = e.receiptPath
            participants = existingShares.map { it.userId }.toSet()
            custom = existingShares.associate { it.userId to it.amount.toString() }
            loaded = true
        } else if (participants.isEmpty()) participants = members.map { it.userId }.toSet()
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) receipt = uri.toString() }
    val amount = amountText.toLongOrNull() ?: 0L
    val customSum = custom.values.sumOf { it.toLongOrNull() ?: 0L }
    val eqCount = participants.size.coerceAtLeast(1)

    fun save() {
        val input = ExpenseInput(
            title = title, category = category, amount = amount, paidByUserId = paidBy, splitMode = mode,
            participantIds = if (mode == SplitMode.EQUAL) participants.toList() else emptyList(),
            customShares = if (mode == SplitMode.CUSTOM) custom.mapNotNull { (k, v) -> v.toLongOrNull()?.takeIf { it > 0 }?.let { k to it } }.toMap() else emptyMap(),
            note = note, receiptPath = receipt
        )
        busy = true
        env.scope.launch {
            val r = if (editing) env.c.trips.updateExpense(env.uid, expenseId, input) else env.c.trips.addExpense(env.uid, tripId, input)
            r.ui(env, if (editing) "Perubahan disimpan" else "Pengeluaran dicatat") { env.nav.popBackStack() }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        HeroHeader(if (editing) "Ubah Pengeluaran" else "Catat Pengeluaran", "Bagi tagihan dengan adil", onBack = { env.nav.popBackStack() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Field("Nama pengeluaran (mis. Sewa Jeep Tour)", title, { title = it })
            Text("Kategori", fontWeight = FontWeight.SemiBold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExpenseCategory.values().forEach { c -> Choice("${catEmoji(c)} ${catLabel(c)}", category == c, { category = c }) }
            }
            Field("Nominal", amountText, { amountText = it.filter(Char::isDigit) }, keyboard = KeyboardType.Number, suffix = "Rp")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(50_000L, 100_000L, 250_000L).forEach { v -> AssistChip(onClick = { amountText = (amount + v).toString() }, label = { Text("+${v / 1000}rb") }) }
            }

            Text("Dibayar oleh", fontWeight = FontWeight.SemiBold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice("Kas Bersama", paidBy == null, { paidBy = null })
                members.forEach { m -> Choice(m.fullName.substringBefore(' '), paidBy == m.userId, { paidBy = m.userId }) }
            }
            if (paidBy == null) Text("Sisa kas saat ini ${rupiah(pool.remaining)}" + if (amount > pool.remaining && !editing) " • tidak cukup" else "", color = if (amount > pool.remaining && !editing) Danger else Muted, fontSize = 12.sp)

            Text("Cara bagi", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice("Bagi Rata", mode == SplitMode.EQUAL, { mode = SplitMode.EQUAL }); Choice("Kustom / Parsial", mode == SplitMode.CUSTOM, { mode = SplitMode.CUSTOM })
            }
            PCard {
                if (mode == SplitMode.EQUAL) {
                    members.forEach { m ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = m.userId in participants, onCheckedChange = { on -> participants = if (on) participants + m.userId else participants - m.userId })
                            Text(m.fullName, modifier = Modifier.weight(1f))
                            if (m.userId in participants && amount > 0) Text(rupiah(amount / eqCount), color = Pine, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Text("Sisa rupiah dibagi 1 per orang dari anggota pertama.", color = Muted, fontSize = 11.sp)
                } else {
                    members.forEach { m ->
                        OutlinedTextField(
                            value = custom[m.userId] ?: "", onValueChange = { v -> custom = custom + (m.userId to v.filter(Char::isDigit)) },
                            label = { Text(m.fullName) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(12.dp)
                        )
                    }
                    val diff = amount - customSum
                    Text(if (diff == 0L && amount > 0) "✅ Total pas" else "Selisih ${rupiah(diff)} (total bagian harus sama dengan nominal)", color = if (diff == 0L && amount > 0) OkGreen else Warn, fontSize = 12.sp)
                }
            }
            Field("Catatan (opsional)", note, { note = it })
            OutlinedButton(onClick = { picker.launch("image/*") }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Text(if (receipt == null) "📷 Lampirkan foto struk" else "📎 Struk terlampir (ketuk untuk ganti)")
            }
        }
        Column(Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding().padding(16.dp)) {
            PButton(if (busy) "Menyimpan…" else if (editing) "Simpan Perubahan" else "Simpan Pengeluaran", { save() }, Modifier.fillMaxWidth(),
                enabled = !busy && title.isNotBlank() && amount > 0 && loaded, accent = true)
        }
    }
}
