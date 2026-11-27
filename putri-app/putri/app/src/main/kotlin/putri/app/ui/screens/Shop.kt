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


// ================================================================ KATALOG OLEH-OLEH
@Composable
fun ShopTab() {
    val env = LocalApp.current
    val me by rememberMe(env)
    val cats by remember { env.c.catalog.productCategories() }.collectAsState(emptyList())
    var cat by remember { mutableStateOf<String?>(null) }
    var q by remember { mutableStateOf("") }
    val all by remember(cat) { if (cat == null) env.c.catalog.products() else env.c.catalog.productsByCategory(cat!!) }.collectAsState(emptyList())
    val products = all.filter { q.isBlank() || it.name.contains(q, ignoreCase = true) }
    val partners by produceState<Map<Long, String>>(emptyMap()) { value = env.c.database.productDao().partners().associate { it.id to it.name } }
    val cart by remember(env.uid) { env.c.cart.observe(env.uid) }.collectAsState(CartSummary(emptyList()))
    val myAllergens = me?.allergenList().orEmpty()

    Column(Modifier.fillMaxSize()) {
        HeroHeader("Oleh-Oleh Khas Banyumas", "Pre-order H-1 • ambil di loker pintar", actions = {
            IconButton(onClick = {
                if (env.session.loggedIn) env.nav.navigate("cart") else { env.toast("Masuk dulu ya"); env.nav.navigate("login") }
            }) {
                BadgedBox(badge = { if (cart.itemCount > 0) Badge { Text("${cart.itemCount}") } }) {
                    Icon(Icons.Filled.ShoppingCart, "Keranjang", tint = Color.White)
                }
            }
        }) {
            OutlinedTextField(
                value = q, onValueChange = { q = it }, placeholder = { Text("Cari mendoan, getuk…", color = Color.White.copy(alpha = .7f)) },
                singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Color.White,
                    focusedBorderColor = Color.White, unfocusedBorderColor = Color.White.copy(alpha = .5f)
                )
            )
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("Semua", cat == null, { cat = null })
            cats.forEach { c -> Choice(c, cat == c, { cat = c }) }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (products.isEmpty()) item { EmptyState("🎁", "Produk tidak ditemukan") }
            items(products, key = { it.id }) { p ->
                val conflict = p.allergenList().filter { it in myAllergens }
                PCard {
                    Row {
                        Box(Modifier.size(64.dp).background(Brush.linearGradient(listOf(Color(0xFFFFE9C2), Sun.copy(alpha = .5f))), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                            Text(if (p.category.contains("Tempe")) "🍢" else if (p.category.contains("Getuk")) "🍠" else "🍪", fontSize = 30.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Text(p.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false)); if (p.bestSeller) Pill("BEST", bg = Sun, fg = Ink) }
                            Text(p.variant, color = Muted, fontSize = 12.sp)
                            Text("🏪 ${partners[p.partnerId] ?: "UMKM"}", fontSize = 12.sp)
                            if (p.packaging.isNotBlank()) Text("📦 ${p.packaging}", color = Muted, fontSize = 11.sp)
                        }
                    }
                    if (conflict.isNotEmpty()) Text("⚠️ Mengandung ${conflict.joinToString()} (sesuai alergi di profilmu)", color = Danger, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(rupiah(p.price), fontWeight = FontWeight.ExtraBold, color = Pine, fontSize = 18.sp)
                            Text(if (p.stock > 0) "Stok ${p.stock}" else "Habis", color = if (p.stock > 0) Muted else Danger, fontSize = 11.sp)
                        }
                        Button(
                            onClick = {
                                if (!env.session.loggedIn) { env.toast("Masuk dulu ya"); env.nav.navigate("login"); return@Button }
                                env.scope.launch { env.c.cart.add(env.uid, p.id).ui(env, "${p.name} masuk keranjang") }
                            },
                            enabled = p.stock > 0 && conflict.isEmpty(), shape = RoundedCornerShape(14.dp)
                        ) { Text(if (p.preOrder) "+ Pre-Order" else "+ Keranjang") }
                    }
                }
            }
        }
    }
}

// ================================================================ KERANJANG
@Composable
fun CartScreen() {
    val env = LocalApp.current
    if (!env.session.loggedIn) { LoginPrompt(); return }
    val cart by remember { env.c.cart.observe(env.uid) }.collectAsState(CartSummary(emptyList()))
    val hubs by remember { env.c.catalog.hubs() }.collectAsState(emptyList())
    var hubId by remember { mutableStateOf<Long?>(null) }
    var day by remember { mutableIntStateOf(0) }
    val now = rememberNow()
    val hub = hubs.firstOrNull { it.id == hubId } ?: hubs.firstOrNull()
    val todayOk = hub != null && timeAt(hub.closeTime, 0) > now + 30 * 60_000L
    if (!todayOk && day == 0 && hub != null) day = 1

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background, contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (cart.rows.isNotEmpty() && hub != null) Column(Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Total (${cart.itemCount} item)", color = Muted, fontSize = 12.sp); Text(rupiah(cart.total), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Pine) }
                    PButton("Lanjut Bayar", {
                        env.scope.launch { env.c.orders.createFromCart(env.uid, hub.id, timeAt(hub.closeTime, day)).ui(env) { env.nav.navigate("checkout/${it.id}") } }
                    }, accent = true)
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
            HeroHeader("Keranjang", "Tersimpan otomatis", onBack = { env.nav.popBackStack() })
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (cart.rows.isEmpty()) EmptyState("🛒", "Keranjangmu masih kosong.\nYuk pilih oleh-oleh khas Banyumas!")
                cart.rows.forEach { r ->
                    PCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.name, fontWeight = FontWeight.Bold)
                                Text("${r.variant} • ${r.partnerName}", color = Muted, fontSize = 12.sp)
                                Text(rupiah(r.price), color = Pine, fontWeight = FontWeight.SemiBold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Stepper(r.qty, { n -> env.scope.launch { env.c.cart.setQty(env.uid, r.productId, n).ui(env) } }, min = 0, max = r.stock.coerceAtLeast(1))
                                if (r.qty >= r.stock) Text("Stok maks.", color = Warn, fontSize = 10.sp)
                            }
                        }
                    }
                }
                if (cart.rows.isNotEmpty()) {
                    Text("Titik Pengambilan", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    hubs.forEach { h -> HubOption(h, selected = hub?.id == h.id) { hubId = h.id } }
                    if (hub != null) PCard {
                        Text("Ambil pada", fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Choice("Hari ini s.d. ${hub.closeTime}", day == 0, { day = 0 }, enabled = todayOk)
                            Choice("Besok s.d. ${hub.closeTime}", day == 1, { day = 1 })
                        }
                        Text("Pesanan hangus bila tidak diambil sebelum batas waktu.", color = Muted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun HubOption(h: HubEntity, selected: Boolean, onClick: () -> Unit) {
    val env = LocalApp.current
    val free by remember(h.id) { env.c.catalog.freeLockerCount(h.id) }.collectAsState(0)
    PCard(onClick = onClick, color = if (selected) Mint else Color.White) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onClick)
            Column(Modifier.weight(1f)) {
                Text(h.name, fontWeight = FontWeight.SemiBold)
                Text("${h.address}\n🕒 ${h.openTime}–${h.closeTime} WIB", color = Muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    if (h.temperatureControlled) Pill("❄️ SUHU DINGIN")
                    Pill("$free loker kosong", bg = if (free > 0) Mint else Color(0xFFFBE3E0), fg = if (free > 0) PineDark else Danger)
                }
            }
        }
    }
}
