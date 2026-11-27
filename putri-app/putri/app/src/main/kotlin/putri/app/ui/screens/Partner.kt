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


// ================================================================ MITRA UMKM
@Composable
fun PartnerScreen() {
    val env = LocalApp.current
    val me by rememberMe(env)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var rev by remember { mutableIntStateOf(0) }
    val partners by produceState<List<PartnerEntity>>(emptyList(), env.uid, rev) { value = env.c.catalog.partnersOf(env.uid) }
    var logout by remember { mutableStateOf(false) }
    var addPartner by remember { mutableStateOf(false) }
    var pname by remember { mutableStateOf("") }

    if (logout) ConfirmDialog("Keluar?", "Kamu akan kembali ke halaman masuk.", "Keluar", { logout = false }) {
        env.session.signOut(); env.nav.navigate("login") { popUpTo(env.nav.graph.id) { inclusive = true } }
    }
    if (addPartner) FormDialog("Tambah Usaha", { addPartner = false }, confirmEnabled = pname.isNotBlank(), onConfirm = {
        env.scope.launch { env.c.catalog.addPartner(env.uid, pname).ui(env, "Usaha ditambahkan") { addPartner = false; pname = ""; rev++ } }
    }) { Field("Nama usaha / toko", pname, { pname = it }) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background, contentWindowInsets = WindowInsets(0),
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                listOf("Produk" to "🎁", "Pesanan" to "📦").forEachIndexed { i, (l, e) ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Text(e, fontSize = 20.sp) }, label = { Text(l) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = Mint, selectedTextColor = Pine))
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            HeroHeader("Dashboard Mitra UMKM", "Halo, ${me?.fullName ?: ""}", actions = { TextButton(onClick = { logout = true }) { Text("Keluar", color = Color.White) } })
            if (partners.isEmpty()) {
                EmptyState("🏪", "Kamu belum punya usaha terdaftar.")
                PButton("Tambah Usaha", { addPartner = true }, Modifier.padding(horizontal = 16.dp))
            } else if (tab == 0) PartnerProducts(partners) { addPartner = true } else PartnerOrders()
        }
    }
}

@Composable
private fun PartnerProducts(partners: List<PartnerEntity>, onAddPartner: () -> Unit) {
    val env = LocalApp.current
    var form by remember { mutableStateOf<ProductEntity?>(null) }
    var del by remember { mutableStateOf<ProductEntity?>(null) }
    form?.let { ProductDialog(partners, it) { form = null } }
    del?.let { p -> ConfirmDialog("Hapus produk?", p.name, "Hapus", { del = null }) { env.scope.launch { env.c.catalog.deleteProduct(env.uid, p.id).ui(env, "Produk dihapus") } } }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            partners.forEach { pt ->
                item(key = "h${pt.id}") { Text("🏪 ${pt.name}", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp) }
                item(key = "p${pt.id}") {
                    val products by remember(pt.id) { env.c.catalog.productsOfPartner(pt.id) }.collectAsState(emptyList())
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (products.isEmpty()) EmptyState("🎁", "Belum ada produk")
                        products.forEach { p ->
                            PCard {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Text(p.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false)); if (!p.active) Pill("NONAKTIF", bg = Color(0xFFEDEDED), fg = Muted) }
                                        Text("${p.variant} • ${p.category}", color = Muted, fontSize = 12.sp)
                                        Text(rupiah(p.price), color = Pine, fontWeight = FontWeight.ExtraBold)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Stok", color = Muted, fontSize = 11.sp)
                                        Stepper(p.stock, { n -> env.scope.launch { env.c.catalog.setStock(env.uid, p.id, n).ui(env) } }, min = 0, max = 9999)
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                    TextButton(onClick = { form = p }) { Text("Ubah") }
                                    TextButton(onClick = { del = p }) { Text("Hapus", color = Danger) }
                                }
                            }
                        }
                        TextButton(onClick = { form = ProductEntity(partnerId = pt.id, name = "", category = "", price = 0, stock = 0) }) { Text("+ Tambah produk ke ${pt.name}") }
                    }
                }
            }
            item { TextButton(onClick = onAddPartner) { Text("+ Tambah usaha lain") } }
        }
    }
}

@Composable
private fun ProductDialog(partners: List<PartnerEntity>, initial: ProductEntity, onDismiss: () -> Unit) {
    val env = LocalApp.current
    val isNew = initial.id == 0L
    var name by remember { mutableStateOf(initial.name) }
    var variant by remember { mutableStateOf(initial.variant) }
    var category by remember { mutableStateOf(initial.category) }
    var price by remember { mutableStateOf(if (initial.price > 0) initial.price.toString() else "") }
    var stock by remember { mutableStateOf(if (isNew) "" else initial.stock.toString()) }
    var pack by remember { mutableStateOf(initial.packaging) }
    var allergens by remember { mutableStateOf(initial.allergens) }
    var pre by remember { mutableStateOf(initial.preOrder) }
    var best by remember { mutableStateOf(initial.bestSeller) }
    var active by remember { mutableStateOf(initial.active) }
    FormDialog(if (isNew) "Produk Baru" else "Ubah Produk", onDismiss, onConfirm = {
        val p = initial.copy(name = name.trim(), variant = variant, category = category.trim(), price = price.toLongOrNull() ?: 0,
            stock = stock.toIntOrNull() ?: 0, packaging = pack, allergens = allergens, preOrder = pre, bestSeller = best, active = active)
        env.scope.launch {
            (if (isNew) env.c.catalog.addProduct(env.uid, p).map { } else env.c.catalog.updateProduct(env.uid, p)).ui(env, "Produk tersimpan") { onDismiss() }
        }
    }) {
        Field("Nama produk", name, { name = it }); Field("Varian / ukuran", variant, { variant = it })
        Field("Kategori (mis. Getuk Sokaraja)", category, { category = it })
        Field("Harga", price, { price = it.filter(Char::isDigit) }, keyboard = KeyboardType.Number, suffix = "Rp")
        Field("Stok", stock, { stock = it.filter(Char::isDigit) }, keyboard = KeyboardType.Number)
        Field("Kemasan", pack, { pack = it }); Field("Alergen (pisahkan koma, mis. kacang,kedelai)", allergens, { allergens = it })
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Pre-Order H-1", Modifier.weight(1f)); Switch(pre, { pre = it }) }
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Best Seller", Modifier.weight(1f)); Switch(best, { best = it }) }
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Tampil di katalog", Modifier.weight(1f)); Switch(active, { active = it }) }
    }
}

@Composable
private fun PartnerOrders() {
    val env = LocalApp.current
    val paid by remember { env.c.orders.queueForPartner(env.uid, OrderStatus.PAID) }.collectAsState(emptyList())
    val ready by remember { env.c.orders.queueForPartner(env.uid, OrderStatus.READY_PICKUP) }.collectAsState(emptyList())
    var pick by remember { mutableStateOf<OrderEntity?>(null) }
    pick?.let { LockerDialog(it) { pick = null } }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Perlu disiapkan (${paid.size})", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp) }
        if (paid.isEmpty()) item { EmptyState("☕", "Tidak ada pesanan baru") }
        items(paid, key = { it.id }) { o -> PartnerOrderCard(o) { pick = o } }
        item { Text("Menunggu diambil (${ready.size})", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, modifier = Modifier.padding(top = 10.dp)) }
        items(ready, key = { "r${it.id}" }) { o -> PartnerOrderCard(o, null) }
    }
}

@Composable
private fun PartnerOrderCard(o: OrderEntity, onPrepare: (() -> Unit)?) {
    val env = LocalApp.current
    val items by remember(o.id) { env.c.database.orderDao().observeItems(o.id) }.collectAsState(emptyList())
    PCard {
        Row { Text(o.code, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text(rupiah(o.total), fontWeight = FontWeight.ExtraBold, color = Pine) }
        items.forEach { Text("• ${it.name} ×${it.qty}", fontSize = 13.sp) }
        o.pickupDeadline?.let { Text("Batas ambil ${fmtDate(it)}", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
        if (onPrepare != null) PButton("Siapkan & Masukkan ke Loker", onPrepare, Modifier.fillMaxWidth().padding(top = 8.dp))
    }
}

@Composable
private fun LockerDialog(o: OrderEntity, onDismiss: () -> Unit) {
    val env = LocalApp.current
    val lockers by remember(o.hubId) { env.c.catalog.lockers(o.hubId ?: -1) }.collectAsState(emptyList())
    var sel by remember { mutableStateOf<String?>(null) }
    FormDialog("Pilih Loker • ${o.code}", onDismiss, "Masukkan", confirmEnabled = sel != null, onConfirm = {
        env.scope.launch { env.c.orders.markReadyForPickup(env.uid, o.id, sel!!).ui(env, "QR terbit untuk pembeli ✅") { onDismiss() } }
    }) {
        val free = lockers.filter { it.status == LockerStatus.FREE }
        if (free.isEmpty()) Text("Semua loker penuh.", color = Danger)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            free.forEach { l -> Choice(l.code, sel == l.code, { sel = l.code }) }
        }
        Text("Taruh pesanan di loker yang dipilih, lalu tekan Masukkan.", color = Muted, fontSize = 12.sp)
    }
}
