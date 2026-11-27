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

import putri.core.payment.PaymentCalculator
import putri.core.pickup.formatCountdown

private fun methodLabel(m: PaymentMethod) = when (m) {
    PaymentMethod.ECO_WALLET -> "Eco-Wallet"
    PaymentMethod.GROUP_POOL -> "Dompet Bersama Grup"
    PaymentMethod.QRIS -> "QRIS (GoPay, OVO, Dana, ShopeePay)"
    PaymentMethod.VA_BCA -> "Virtual Account BCA"
    PaymentMethod.VA_MANDIRI -> "Virtual Account Mandiri"
    PaymentMethod.VA_BRI_BSI -> "Virtual Account BRI / BSI"
}

// ================================================================ CHECKOUT
@Composable
fun CheckoutScreen(orderId: Long) {
    val env = LocalApp.current
    val me by rememberMe(env)
    val detail by produceState<OrderDetail?>(null, orderId) { value = env.c.orders.detail(orderId) }
    val trips by remember { env.c.trips.myTrips(env.uid) }.collectAsState(emptyList())
    var usePoints by remember { mutableStateOf(false) }
    var method by remember { mutableStateOf(PaymentMethod.ECO_WALLET) }
    var tripId by remember { mutableStateOf<Long?>(null) }
    var confirmCancel by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val now = rememberNow()
    val d = detail
    val u = me
    if (d == null || u == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return }
    val o = d.order
    if (o.status != OrderStatus.WAITING_PAYMENT) {
        Column(Modifier.fillMaxSize()) {
            HeroHeader("Pembayaran", o.code, onBack = { env.nav.popBackStack() })
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val (c, label) = statusColor(o.status)
                Pill(label, bg = c, fg = Color.White)
                Spacer(Modifier.height(12.dp))
                Text("Pesanan ini tidak lagi menunggu pembayaran.", color = Muted, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                PButton("Lihat Detail Pesanan", { env.nav.navigate("order/$orderId") { popUpTo("checkout/{id}") { inclusive = true } } })
            }
        }
        return
    }
    if (method == PaymentMethod.GROUP_POOL && tripId == null) tripId = trips.firstOrNull()?.id
    val remaining = (o.paymentExpiresAt - now).coerceAtLeast(0)
    val co = PaymentCalculator.checkout(o.subtotal, u.ecoPoints, usePoints)
    val canPay = remaining > 0 && !busy &&
        (method != PaymentMethod.ECO_WALLET || u.walletBalance >= co.total) &&
        (method != PaymentMethod.GROUP_POOL || tripId != null)

    if (confirmCancel) ConfirmDialog("Batalkan pesanan?", "Stok akan dikembalikan.", "Batalkan", { confirmCancel = false }) {
        env.scope.launch { env.c.orders.cancel(orderId).ui(env, "Pesanan dibatalkan") { env.nav.popBackStack() } }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background, contentWindowInsets = WindowInsets(0),
        bottomBar = {
            Column(Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding().padding(16.dp)) {
                PButton(if (remaining > 0) "Bayar ${rupiah(co.total)}" else "Waktu pembayaran habis", {
                    busy = true
                    env.scope.launch {
                        env.c.orders.pay(orderId, method, usePoints, tripId).ui(env, "Pembayaran berhasil 🎉") {
                            env.nav.navigate("order/$orderId") { popUpTo("checkout/{id}") { inclusive = true } }
                        }
                        busy = false
                    }
                }, Modifier.fillMaxWidth(), enabled = canPay, accent = true)
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
            HeroHeader("Pembayaran", o.code, onBack = { env.nav.popBackStack() }) {
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("⏳ Selesaikan dalam ", color = Color.White.copy(alpha = .85f))
                    Text(formatCountdown(remaining), color = Sun, fontWeight = FontWeight.ExtraBold)
                }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PCard {
                    Text("Ringkasan Pesanan", fontWeight = FontWeight.Bold)
                    d.items.forEach { LabelValue("${it.name} ×${it.qty}", rupiah(it.unitPrice * it.qty)) }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    LabelValue("Subtotal", rupiah(o.subtotal))
                    if (usePoints && co.pointsUsed > 0) LabelValue("Potongan ${co.pointsUsed} poin", "-${rupiah(co.discount)}")
                    LabelValue("Total bayar", rupiah(co.total), bold = true)
                    if (co.pointsEarned > 0) Text("🌿 Kamu akan dapat +${co.pointsEarned} Eco-Points", color = Pine, fontSize = 12.sp)
                }
                PCard(color = Mint) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Tukar Eco-Points", fontWeight = FontWeight.Bold, color = PineDark)
                            Text("Kamu punya ${u.ecoPoints} poin (24 poin = Rp1.000)", color = PineDark, fontSize = 12.sp)
                        }
                        Switch(checked = usePoints, onCheckedChange = { usePoints = it }, enabled = u.ecoPoints >= 24)
                    }
                }
                Text("Metode Pembayaran", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                PaymentMethod.values().forEach { m ->
                    val disabled = (m == PaymentMethod.GROUP_POOL && trips.isEmpty())
                    PCard(onClick = { if (!disabled) method = m }, color = if (method == m) Mint else Color.White) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = method == m, onClick = { if (!disabled) method = m }, enabled = !disabled)
                            Column(Modifier.weight(1f)) {
                                Text(methodLabel(m), fontWeight = FontWeight.SemiBold, color = if (disabled) Muted else Ink)
                                when (m) {
                                    PaymentMethod.ECO_WALLET -> Text("Saldo ${rupiah(u.walletBalance)}" + if (u.walletBalance < co.total) " • tidak cukup" else "", color = if (u.walletBalance < co.total) Danger else Muted, fontSize = 12.sp)
                                    PaymentMethod.GROUP_POOL -> Text(if (disabled) "Kamu belum punya grup trip" else "Dibagi rata ke seluruh anggota grup", color = Muted, fontSize = 12.sp)
                                    else -> Text("Simulasi pembayaran (demo)", color = Muted, fontSize = 12.sp)
                                }
                            }
                        }
                        if (m == PaymentMethod.GROUP_POOL && method == m && trips.isNotEmpty()) {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                trips.forEach { t -> Choice(t.name, tripId == t.id, { tripId = t.id }) }
                            }
                            tripId?.let { tid ->
                                val pool by remember(tid) { env.c.trips.pool(tid) }.collectAsState(PoolStatus(0, 0))
                                Text("Sisa kas: ${rupiah(pool.remaining)}" + if (pool.remaining < co.total) " • tidak cukup" else "", color = if (pool.remaining < co.total) Danger else PineDark, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
                TextButton(onClick = { confirmCancel = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Batalkan pesanan", color = Danger) }
            }
        }
    }
}

// ================================================================ RIWAYAT PESANAN
@Composable
fun OrdersScreen() {
    val env = LocalApp.current
    if (!env.session.loggedIn) { LoginPrompt(); return }
    val orders by remember { env.c.orders.myOrders(env.uid) }.collectAsState(emptyList())
    Column(Modifier.fillMaxSize()) {
        HeroHeader("Pesanan & Pass Saya", "Pickup Hub, tiket, dan riwayat", onBack = { env.nav.popBackStack() })
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (orders.isEmpty()) item { EmptyState("🎫", "Belum ada pesanan") }
            items(orders, key = { it.id }) { o -> OrderCard(o) }
        }
    }
}

@Composable
fun OrderCard(o: OrderEntity) {
    val env = LocalApp.current
    val (c, label) = statusColor(o.status)
    PCard(onClick = { env.nav.navigate(if (o.status == OrderStatus.WAITING_PAYMENT) "checkout/${o.id}" else "order/${o.id}") }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (o.kind == OrderKind.TIKET) "🎫" else "🎁", fontSize = 28.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(o.code, fontWeight = FontWeight.Bold)
                Text(fmtDate(o.createdAt), color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Pill(label, bg = c, fg = Color.White)
            }
            Text(rupiah(o.total), fontWeight = FontWeight.ExtraBold, color = Pine)
        }
    }
}

// ================================================================ DETAIL PESANAN / E-TIKET
@Composable
fun OrderDetailScreen(id: Long) {
    val env = LocalApp.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(3000); tick++ } }   // ikuti status terbaru (mis. UMKM selesai menyiapkan)
    val detail by produceState<OrderDetail?>(null, id, tick) { value = env.c.orders.detail(id) }
    val now = rememberNow()
    var confirmCancel by remember { mutableStateOf(false) }
    val d = detail
    if (d == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return }
    val o = d.order
    val pass = d.pass
    val hubName by produceState<String?>(null, o.hubId) { value = o.hubId?.let { h -> env.c.catalog.hubs().first().firstOrNull { it.id == h }?.name } }
    val (c, label) = statusColor(o.status)

    if (confirmCancel) ConfirmDialog(
        "Batalkan pesanan?",
        if (o.status == OrderStatus.PAID) "Dana dikembalikan ke Eco-Wallet dan stok dilepas." else "Stok akan dikembalikan.",
        "Batalkan", { confirmCancel = false }
    ) { env.scope.launch { env.c.orders.cancel(id).ui(env, "Pesanan dibatalkan") { tick++ } } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        HeroHeader(if (o.kind == OrderKind.TIKET) "E-Tiket" else "Detail Pesanan", o.code, onBack = { env.nav.popBackStack() }) {
            Spacer(Modifier.height(6.dp)); Pill(label, bg = c, fg = Color.White)
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                pass != null && pass.state == PassState.ACTIVE && o.status == OrderStatus.READY_PICKUP -> PCard {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (pass.kind == PassKind.GATE) "Scan di Gerbang" else "Tunjukkan ke Petugas / Scan di Loker", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        QrImage(pass.token, 230.dp)
                        Spacer(Modifier.height(10.dp))
                        Text("KODE CADANGAN", color = Muted, fontSize = 11.sp)
                        Text(pass.backupCode, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = PineDark, letterSpacing = 2.sp)
                        Spacer(Modifier.height(8.dp))
                        val left = (pass.expiresAt - now).coerceAtLeast(0)
                        Text("⏳ Berlaku ${formatCountdown(left)} lagi", color = if (left < 30 * 60_000L) Danger else Pine, fontWeight = FontWeight.SemiBold)
                        Text(if (pass.kind == PassKind.GATE) "Gerbang: ${pass.lockerCode}" else "Loker: ${pass.lockerCode}", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                pass != null && pass.state == PassState.USED -> PCard(color = Mint) {
                    Text("✅ Pass sudah digunakan", fontWeight = FontWeight.Bold, color = PineDark)
                    pass.redeemedAt?.let { Text(fmtDate(it), color = PineDark, fontSize = 12.sp) }
                }
                o.status == OrderStatus.PAID -> PCard(color = Color(0xFFFFF3DC)) {
                    Text("👩‍🍳 UMKM sedang menyiapkan pesananmu", fontWeight = FontWeight.Bold)
                    Text("QR Pickup akan muncul di sini begitu pesanan masuk ke loker.", color = Muted, fontSize = 13.sp)
                }
                o.status == OrderStatus.EXPIRED -> PCard(color = Color(0xFFFBE3E0)) { Text("Pesanan kedaluwarsa. Stok dilepas kembali.", color = Danger) }
                o.status == OrderStatus.CANCELLED -> PCard(color = Color(0xFFFBE3E0)) { Text("Pesanan dibatalkan.", color = Danger) }
            }

            PCard {
                Text("Informasi", fontWeight = FontWeight.Bold)
                LabelValue("Kode transaksi", o.trxCode)
                LabelValue("Dibuat", fmtDate(o.createdAt))
                hubName?.let { LabelValue("Titik pickup", it) }
                o.pickupDeadline?.let { LabelValue(if (o.kind == OrderKind.TIKET) "Berlaku s.d." else "Batas ambil", fmtDate(it)) }
                d.payment?.let { LabelValue("Metode bayar", methodLabel(it.method)) }
            }
            PCard {
                Text("Rincian", fontWeight = FontWeight.Bold)
                d.items.forEach { LabelValue("${it.name} ×${it.qty}\n${it.subtitle}", rupiah(it.unitPrice * it.qty)) }
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                LabelValue("Subtotal", rupiah(o.subtotal))
                if (o.discount > 0) LabelValue("Poin (${o.pointsUsed})", "-${rupiah(o.discount)}")
                LabelValue("Total", rupiah(o.total), bold = true)
            }
            if (o.kind == OrderKind.OLEH_OLEH && o.status == OrderStatus.READY_PICKUP) PCard(color = Mint) {
                Text("Cara Ambil", fontWeight = FontWeight.Bold, color = PineDark)
                listOf("1️⃣ Datang ke titik pickup sebelum batas waktu", "2️⃣ Tunjukkan QR / sebutkan kode cadangan ke petugas", "3️⃣ Ambil pesanan dari loker. Selesai!").forEach { Text(it, color = PineDark, fontSize = 13.sp) }
            }
            if (o.status == OrderStatus.WAITING_PAYMENT) PButton("Bayar Sekarang", { env.nav.navigate("checkout/$id") }, Modifier.fillMaxWidth(), accent = true)
            if (o.status == OrderStatus.WAITING_PAYMENT || o.status == OrderStatus.PAID) {
                TextButton(onClick = { confirmCancel = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Batalkan pesanan", color = Danger) }
            }
        }
    }
}

// ================================================================ DOMPET & POIN
@Composable
fun WalletScreen() {
    val env = LocalApp.current
    if (!env.session.loggedIn) { LoginPrompt(); return }
    val me by rememberMe(env)
    val tx by remember { env.c.wallet.transactions(env.uid) }.collectAsState(emptyList())
    val pts by remember { env.c.wallet.pointHistory(env.uid) }.collectAsState(emptyList())
    var tab by remember { mutableIntStateOf(0) }
    var topUp by remember { mutableStateOf(false) }
    if (topUp) TopUpDialog { topUp = false }

    Column(Modifier.fillMaxSize()) {
        HeroHeader("Dompet & Eco-Points", "Mutasi saldo dan poin", onBack = { env.nav.popBackStack() }) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(rupiah(me?.walletBalance ?: 0), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
                    Text("🌿 ${me?.ecoPoints ?: 0} Eco-Points", color = Color.White.copy(alpha = .9f))
                }
                Button(onClick = { topUp = true }, colors = ButtonDefaults.buttonColors(containerColor = Sun, contentColor = Ink)) { Text("Top Up", fontWeight = FontWeight.Bold) }
            }
        }
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("Saldo", tab == 0, { tab = 0 }); Choice("Eco-Points", tab == 1, { tab = 1 })
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (tab == 0) {
                if (tx.isEmpty()) item { EmptyState("👛", "Belum ada mutasi saldo") }
                items(tx, key = { it.id }) { t ->
                    val plus = t.type in listOf(WalletTxType.TOPUP, WalletTxType.REFUND, WalletTxType.SETTLEMENT_IN)
                    val name = when (t.type) {
                        WalletTxType.TOPUP -> "Top Up"; WalletTxType.PAYMENT -> "Pembayaran"; WalletTxType.REFUND -> "Refund"
                        WalletTxType.POOL_CONTRIBUTION -> "Isi Kas Grup"; WalletTxType.SETTLEMENT -> "Bayar Patungan"; WalletTxType.SETTLEMENT_IN -> "Terima Patungan"
                    }
                    PCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text(name, fontWeight = FontWeight.SemiBold); Text("${t.ref} • ${fmtDate(t.createdAt)}", color = Muted, fontSize = 11.sp) }
                            Column(horizontalAlignment = Alignment.End) {
                                Text((if (plus) "+" else "-") + rupiah(t.amount), fontWeight = FontWeight.Bold, color = if (plus) OkGreen else Danger)
                                Text("Saldo ${rupiah(t.balanceAfter)}", color = Muted, fontSize = 11.sp)
                            }
                        }
                    }
                }
            } else {
                if (pts.isEmpty()) item { EmptyState("🌿", "Belum ada riwayat poin") }
                items(pts, key = { it.id }) { p ->
                    PCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text(p.reason, fontWeight = FontWeight.SemiBold); Text(fmtDate(p.createdAt), color = Muted, fontSize = 11.sp) }
                            Text((if (p.delta > 0) "+" else "") + "${p.delta}", fontWeight = FontWeight.ExtraBold, color = if (p.delta > 0) OkGreen else Danger)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
fun TopUpDialog(onDismiss: () -> Unit) {
    val env = LocalApp.current
    var amount by remember { mutableStateOf("100000") }
    var via by remember { mutableStateOf(PaymentMethod.QRIS) }
    FormDialog("Top Up Eco-Wallet", onDismiss, "Top Up", onConfirm = {
        env.scope.launch {
            env.c.wallet.topUp(env.uid, amount.toLongOrNull() ?: 0, via).ui(env, "Top up berhasil") { onDismiss() }
        }
    }) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(50_000L, 100_000L, 250_000L, 500_000L).forEach { v -> Choice(rupiah(v), amount == v.toString(), { amount = v.toString() }) }
        }
        Field("Nominal", amount, { amount = it.filter(Char::isDigit) }, keyboard = KeyboardType.Number)
        Text("Metode", fontWeight = FontWeight.SemiBold)
        listOf(PaymentMethod.QRIS, PaymentMethod.VA_BCA, PaymentMethod.VA_MANDIRI, PaymentMethod.VA_BRI_BSI).forEach { m ->
            Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(via == m, { via = m }); Text(methodLabel(m), fontSize = 13.sp) }
        }
        Text("Simulasi pembayaran (demo).", color = Muted, fontSize = 11.sp)
    }
}
