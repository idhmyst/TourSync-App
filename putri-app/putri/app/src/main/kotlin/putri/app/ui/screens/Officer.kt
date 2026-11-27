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

import putri.core.budget.TravelStyle
import putri.core.pickup.PassStatus

// ================================================================ PETUGAS HUB
@Composable
fun OfficerScreen() {
    val env = LocalApp.current
    val me by rememberMe(env)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var logout by remember { mutableStateOf(false) }
    if (logout) ConfirmDialog("Keluar?", "Kamu akan kembali ke halaman masuk.", "Keluar", { logout = false }) {
        env.session.signOut(); env.nav.navigate("login") { popUpTo(env.nav.graph.id) { inclusive = true } }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background, contentWindowInsets = WindowInsets(0),
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                listOf("Scan" to "📷", "Loker" to "🗄️", "Katalog" to "🌲").forEachIndexed { i, (l, e) ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Text(e, fontSize = 20.sp) }, label = { Text(l) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = Mint, selectedTextColor = Pine))
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            HeroHeader("Petugas Pickup Hub", "Halo, ${me?.fullName ?: ""}", actions = { TextButton(onClick = { logout = true }) { Text("Keluar", color = Color.White) } })
            when (tab) { 0 -> ScanTab(); 1 -> HubsTab(); else -> CatalogTab() }
        }
    }
}

// ---------------------------------------------------------------- scan / validasi
@Composable
private fun ScanTab() {
    val env = LocalApp.current
    var code by remember { mutableStateOf("") }
    var where by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<RedeemResult?>(null) }
    var busy by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PCard(color = Mint) {
            Text("Validasi Pass Pembeli", fontWeight = FontWeight.ExtraBold, color = PineDark)
            Text("Tempel isi QR atau ketik kode cadangan (BTR-XXXX-XXXX) dari layar pembeli. Kamera pemindai bisa ditambahkan terpisah.", color = PineDark, fontSize = 13.sp)
        }
        Field("Isi QR / kode cadangan", code, { code = it; result = null }, singleLine = false)
        Field("Kode loker / gerbang (mis. LOKER-08, GATE-1)", where, { where = it.trim().uppercase(); result = null })
        PButton(if (busy) "Memeriksa…" else "Validasi & Serahkan", {
            busy = true
            env.scope.launch { result = env.c.orders.redeem(env.uid, code, where); busy = false }
        }, Modifier.fillMaxWidth(), enabled = !busy && code.isNotBlank() && where.isNotBlank(), accent = true)

        result?.let { r ->
            val (emoji, text, color) = when (r.status) {
                PassStatus.READY -> Triple("✅", "Berhasil! Pesanan diserahkan.", OkGreen)
                PassStatus.USED -> Triple("⚠️", "Pass sudah pernah dipakai.", Warn)
                PassStatus.EXPIRED -> Triple("⌛", "Pass sudah kedaluwarsa.", Danger)
                PassStatus.WRONG_LOCKER -> Triple("🚫", "Loker/gerbang tidak sesuai dengan pass.", Danger)
                PassStatus.INVALID -> Triple("❌", "Pass tidak valid atau akun ini bukan petugas.", Danger)
            }
            PCard(color = color.copy(alpha = .12f)) {
                Text("$emoji $text", fontWeight = FontWeight.ExtraBold, color = color, fontSize = 17.sp)
                r.order?.let { o ->
                    LabelValue("Pesanan", o.code); LabelValue("Total", rupiah(o.total))
                    o.completedAt?.let { LabelValue("Selesai", fmtDate(it)) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- hub & loker
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HubsTab() {
    val env = LocalApp.current
    val hubs by remember { env.c.catalog.hubs() }.collectAsState(emptyList())
    var hubForm by remember { mutableStateOf<HubEntity?>(null) }
    var addLockerTo by remember { mutableStateOf<HubEntity?>(null) }
    var lockerSel by remember { mutableStateOf<LockerEntity?>(null) }
    var delHub by remember { mutableStateOf<HubEntity?>(null) }
    var lockerCode by remember { mutableStateOf("") }

    hubForm?.let { HubDialog(it) { hubForm = null } }
    addLockerTo?.let { h ->
        FormDialog("Tambah Loker • ${h.name}", { addLockerTo = null }, confirmEnabled = lockerCode.isNotBlank(), onConfirm = {
            env.scope.launch { env.c.catalog.addLocker(env.uid, h.id, lockerCode).ui(env, "Loker ditambahkan") { addLockerTo = null; lockerCode = "" } }
        }) { Field("Kode loker (mis. LOKER-09)", lockerCode, { lockerCode = it.trim().uppercase() }) }
    }
    lockerSel?.let { l ->
        AlertDialog(
            onDismissRequest = { lockerSel = null }, title = { Text(l.code, fontWeight = FontWeight.Bold) },
            text = { Text("Status: ${l.status.name}") },
            confirmButton = {
                TextButton(onClick = {
                    val next = if (l.status == LockerStatus.MAINTENANCE) LockerStatus.FREE else LockerStatus.MAINTENANCE
                    env.scope.launch { env.c.catalog.setLockerStatus(env.uid, l.code, next).ui(env, "Status diubah") }
                    lockerSel = null
                }) { Text(if (l.status == LockerStatus.MAINTENANCE) "Aktifkan" else "Tandai Perawatan") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { env.scope.launch { env.c.catalog.deleteLocker(env.uid, l.code).ui(env, "Loker dihapus") }; lockerSel = null }) { Text("Hapus", color = Danger) }
                    TextButton(onClick = { lockerSel = null }) { Text("Tutup") }
                }
            }
        )
    }
    delHub?.let { h -> ConfirmDialog("Hapus hub?", "${h.name} beserta semua lokernya.", "Hapus", { delHub = null }) { env.scope.launch { env.c.catalog.deleteHub(env.uid, h.id).ui(env, "Hub dihapus") } } }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(hubs, key = { it.id }) { h ->
            val lockers by remember(h.id) { env.c.catalog.lockers(h.id) }.collectAsState(emptyList())
            PCard {
                Text(h.name, fontWeight = FontWeight.ExtraBold)
                Text("${h.address}\n🕒 ${h.openTime}–${h.closeTime}" + if (h.temperatureControlled) " • ❄️ pendingin" else "", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    lockers.forEach { l ->
                        val (bg, fg) = when (l.status) {
                            LockerStatus.FREE -> Mint to PineDark; LockerStatus.OCCUPIED -> Sun to Ink; LockerStatus.MAINTENANCE -> Color(0xFFEDEDED) to Muted
                        }
                        Text(l.code, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.background(bg, RoundedCornerShape(10.dp)).clickable { lockerSel = l }.padding(horizontal = 10.dp, vertical = 8.dp))
                    }
                }
                Text("Hijau kosong • Kuning terisi • Abu perawatan", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                Row {
                    TextButton(onClick = { addLockerTo = h }) { Text("+ Loker") }
                    TextButton(onClick = { hubForm = h }) { Text("Ubah") }
                    TextButton(onClick = { delHub = h }) { Text("Hapus", color = Danger) }
                }
            }
        }
        item { PButton("+ Tambah Pickup Hub", { hubForm = HubEntity(name = "", address = "") }, Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun HubDialog(initial: HubEntity, onDismiss: () -> Unit) {
    val env = LocalApp.current
    val isNew = initial.id == 0L
    var name by remember { mutableStateOf(initial.name) }; var addr by remember { mutableStateOf(initial.address) }
    var desc by remember { mutableStateOf(initial.description) }; var open by remember { mutableStateOf(initial.openTime) }
    var close by remember { mutableStateOf(initial.closeTime) }; var cold by remember { mutableStateOf(initial.temperatureControlled) }
    FormDialog(if (isNew) "Hub Baru" else "Ubah Hub", onDismiss, onConfirm = {
        val h = initial.copy(name = name.trim(), address = addr.trim(), description = desc, openTime = open, closeTime = close, temperatureControlled = cold)
        env.scope.launch { (if (isNew) env.c.catalog.addHub(env.uid, h).map { } else env.c.catalog.updateHub(env.uid, h)).ui(env, "Hub tersimpan") { onDismiss() } }
    }) {
        Field("Nama hub", name, { name = it }); Field("Alamat", addr, { addr = it }); Field("Deskripsi", desc, { desc = it })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Field("Buka (HH:mm)", open, { open = it }, Modifier.weight(1f)); Field("Tutup (HH:mm)", close, { close = it }, Modifier.weight(1f)) }
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Loker berpendingin", Modifier.weight(1f)); Switch(cold, { cold = it }) }
    }
}

// ---------------------------------------------------------------- katalog destinasi & menu
@Composable
private fun CatalogTab() {
    val env = LocalApp.current
    val dests by remember { env.c.catalog.destinations() }.collectAsState(emptyList())
    val meals by remember { env.c.catalog.meals() }.collectAsState(emptyList())
    var destForm by remember { mutableStateOf<DestinationEntity?>(null) }
    var ticketsFor by remember { mutableStateOf<DestinationEntity?>(null) }
    var mealForm by remember { mutableStateOf<MealEntity?>(null) }
    var delDest by remember { mutableStateOf<DestinationEntity?>(null) }
    var delMeal by remember { mutableStateOf<MealEntity?>(null) }

    destForm?.let { DestinationDialog(it) { destForm = null } }
    ticketsFor?.let { TicketsDialog(it) { ticketsFor = null } }
    mealForm?.let { MealDialog(it) { mealForm = null } }
    delDest?.let { d -> ConfirmDialog("Hapus destinasi?", "${d.name} beserta paket tiketnya.", "Hapus", { delDest = null }) { env.scope.launch { env.c.catalog.deleteDestination(d.id).ui(env, "Destinasi dihapus") } } }
    delMeal?.let { m -> ConfirmDialog("Hapus menu?", m.name, "Hapus", { delMeal = null }) { env.scope.launch { env.c.catalog.deleteMeal(m.id).ui(env, "Menu dihapus") } } }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Destinasi Wisata", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp) }
        items(dests, key = { "d${it.id}" }) { d ->
            PCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(styleEmoji(d.style), fontSize = 26.sp); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) { Text(d.name, fontWeight = FontWeight.Bold); Text("${d.area} • ${rupiah(d.ticketPrice)}", color = Muted, fontSize = 12.sp) }
                }
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { ticketsFor = d }) { Text("Tiket") }
                    TextButton(onClick = { destForm = d }) { Text("Ubah") }
                    TextButton(onClick = { delDest = d }) { Text("Hapus", color = Danger) }
                }
            }
        }
        item { PButton("+ Tambah Destinasi", { destForm = DestinationEntity(name = "", area = "", address = "", description = "", style = TravelStyle.ALAM_RELAKSASI, ticketPrice = 0) }, Modifier.fillMaxWidth()) }
        item { Text("Menu Kuliner (untuk Smart Budget)", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, modifier = Modifier.padding(top = 12.dp)) }
        items(meals, key = { "m${it.id}" }) { m ->
            PCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(m.name, fontWeight = FontWeight.Bold); Text(m.partnerName, color = Muted, fontSize = 12.sp) }
                    Text(rupiah(m.pricePerPerson), fontWeight = FontWeight.ExtraBold, color = Pine)
                }
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { mealForm = m }) { Text("Ubah") }
                    TextButton(onClick = { delMeal = m }) { Text("Hapus", color = Danger) }
                }
            }
        }
        item { PButton("+ Tambah Menu", { mealForm = MealEntity(name = "", partnerName = "", pricePerPerson = 0) }, Modifier.fillMaxWidth()) }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun DestinationDialog(initial: DestinationEntity, onDismiss: () -> Unit) {
    val env = LocalApp.current
    val isNew = initial.id == 0L
    var name by remember { mutableStateOf(initial.name) }; var area by remember { mutableStateOf(initial.area) }
    var addr by remember { mutableStateOf(initial.address) }; var desc by remember { mutableStateOf(initial.description) }
    var style by remember { mutableStateOf(initial.style) }; var price by remember { mutableStateOf(initial.ticketPrice.toString()) }
    var open by remember { mutableStateOf(initial.openTime) }; var close by remember { mutableStateOf(initial.closeTime) }
    var rating by remember { mutableStateOf(initial.rating.toString()) }; var hl by remember { mutableStateOf(initial.highlights) }
    var fac by remember { mutableStateOf(initial.facilities) }; var eco by remember { mutableStateOf(initial.ecoCertified) }
    FormDialog(if (isNew) "Destinasi Baru" else "Ubah Destinasi", onDismiss, onConfirm = {
        val d = initial.copy(name = name.trim(), area = area, address = addr, description = desc, style = style, ticketPrice = price.toLongOrNull() ?: 0,
            openTime = open, closeTime = close, rating = rating.toDoubleOrNull() ?: 0.0, highlights = hl, facilities = fac, ecoCertified = eco)
        env.scope.launch { (if (isNew) env.c.catalog.addDestination(d).map { } else env.c.catalog.updateDestination(d)).ui(env, "Destinasi tersimpan") { onDismiss() } }
    }) {
        Field("Nama destinasi", name, { name = it }); Field("Area (mis. BATURRADEN TIMUR)", area, { area = it }); Field("Alamat", addr, { addr = it })
        Field("Deskripsi", desc, { desc = it }, singleLine = false)
        Text("Gaya", fontWeight = FontWeight.SemiBold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { TravelStyle.values().forEach { s -> Choice(s.label, style == s, { style = s }) } }
        Field("Harga tiket dasar", price, { price = it.filter(Char::isDigit) }, keyboard = KeyboardType.Number, suffix = "Rp")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Field("Buka", open, { open = it }, Modifier.weight(1f)); Field("Tutup", close, { close = it }, Modifier.weight(1f)) }
        Field("Rating (0-5)", rating, { rating = it }, keyboard = KeyboardType.Decimal)
        Field("Daya tarik (pisahkan koma)", hl, { hl = it }); Field("Fasilitas (pisahkan koma)", fac, { fac = it })
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Eco-Certified", Modifier.weight(1f)); Switch(eco, { eco = it }) }
    }
}

@Composable
private fun TicketsDialog(d: DestinationEntity, onDismiss: () -> Unit) {
    val env = LocalApp.current
    val tickets by remember(d.id) { env.c.catalog.tickets(d.id) }.collectAsState(emptyList())
    var name by remember { mutableStateOf("") }; var price by remember { mutableStateOf("") }; var unit by remember { mutableStateOf("orang") }
    FormDialog("Tiket • ${d.name}", onDismiss, "Tambah", confirmEnabled = name.isNotBlank() && price.isNotBlank(), onConfirm = {
        env.scope.launch {
            env.c.catalog.addTicket(TicketPackageEntity(destinationId = d.id, name = name.trim(), price = price.toLongOrNull() ?: 0, unitLabel = unit)).ui(env, "Paket ditambahkan") { name = ""; price = "" }
        }
    }) {
        tickets.forEach { t ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(t.name, fontWeight = FontWeight.SemiBold); Text("${rupiah(t.price)}/${t.unitLabel}", color = Muted, fontSize = 12.sp) }
                TextButton(onClick = { env.scope.launch { env.c.catalog.deleteTicket(t.id).ui(env, "Paket dihapus") } }) { Text("Hapus", color = Danger) }
            }
        }
        HorizontalDivider()
        Text("Paket baru", fontWeight = FontWeight.SemiBold)
        Field("Nama paket", name, { name = it }); Field("Harga", price, { price = it.filter(Char::isDigit) }, keyboard = KeyboardType.Number, suffix = "Rp"); Field("Satuan", unit, { unit = it })
    }
}

@Composable
private fun MealDialog(initial: MealEntity, onDismiss: () -> Unit) {
    val env = LocalApp.current
    val isNew = initial.id == 0L
    var name by remember { mutableStateOf(initial.name) }; var partner by remember { mutableStateOf(initial.partnerName) }
    var price by remember { mutableStateOf(if (initial.pricePerPerson > 0) initial.pricePerPerson.toString() else "") }
    FormDialog(if (isNew) "Menu Baru" else "Ubah Menu", onDismiss, onConfirm = {
        val m = initial.copy(name = name.trim(), partnerName = partner, pricePerPerson = price.toLongOrNull() ?: 0)
        env.scope.launch { (if (isNew) env.c.catalog.addMeal(m).map { } else env.c.catalog.updateMeal(m)).ui(env, "Menu tersimpan") { onDismiss() } }
    }) {
        Field("Nama menu", name, { name = it }); Field("Warung / mitra", partner, { partner = it })
        Field("Harga per orang", price, { price = it.filter(Char::isDigit) }, keyboard = KeyboardType.Number, suffix = "Rp")
    }
}
