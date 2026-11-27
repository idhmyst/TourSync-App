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
import androidx.compose.runtime.saveable.rememberSaveable
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

// ================================================================ ONBOARDING
@Composable
fun OnboardingScreen() {
    val env = LocalApp.current
    fun go(route: String) {
        env.session.finishOnboarding()
        env.nav.navigate(route) { popUpTo("onboarding") { inclusive = true } }
    }
    Column(
        Modifier.fillMaxSize().background(heroBrush).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(24.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("🌲 PassGo", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { go("login") }) { Text("Lewati", color = Color.White.copy(alpha = .85f)) }
        }
        Spacer(Modifier.height(28.dp))
        Pill("WISATA BERKELANJUTAN BANYUMAS", bg = Sun, fg = Ink)
        Spacer(Modifier.height(12.dp))
        Text("Jelajah Banyumas,\nLebih Mudah & Hemat", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 38.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            "Rencanakan liburan sesuai anggaran, patungan bareng teman, dan bawa pulang oleh-oleh UMKM tanpa repot.",
            color = Color.White.copy(alpha = .85f)
        )
        Spacer(Modifier.height(22.dp))
        listOf(
            "💰" to ("Smart Budget" to "Rekomendasi paket wisata otomatis sesuai uangmu"),
            "🧾" to ("Split-Bill Grup" to "Kas bersama & patungan adil tanpa ribet"),
            "🎁" to ("Oleh-Oleh UMKM" to "Pre-order, ambil di loker pintar sebelum pulang")
        ).forEach { (emoji, t) ->
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp).background(Color.White.copy(alpha = .12f), RoundedCornerShape(16.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(emoji, fontSize = 28.sp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(t.first, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(t.second, color = Color.White.copy(alpha = .8f), fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp)).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📍 Kabupaten Banyumas", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Pill("AKTIF", bg = OkGreen, fg = Color.White)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Cilacap", "Purbalingga", "Banjarnegara").forEach { Pill("$it • Segera", bg = Color(0xFFEDEDED), fg = Muted) }
            }
        }
        Spacer(Modifier.height(24.dp))
        PButton("Mulai Petualangan", { go("login") }, Modifier.fillMaxWidth(), accent = true)
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = { go("register") }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            border = BorderStroke(1.dp, Color.White.copy(alpha = .7f))
        ) { Text("Daftar Akun Baru", fontWeight = FontWeight.SemiBold) }
        TextButton(onClick = { env.session.guest = true; go("home") }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Jelajah sebagai Tamu", color = Color.White)
        }
    }
}

// ================================================================ LOGIN
private fun roleLabel(r: UserRole) = when (r) {
    UserRole.WISATAWAN -> "Wisatawan"
    UserRole.MITRA_UMKM -> "Mitra UMKM"
    UserRole.PETUGAS_HUB -> "Petugas Hub"
}

@Composable
fun LoginScreen() {
    val env = LocalApp.current
    var role by remember { mutableStateOf(UserRole.WISATAWAN) }
    var id by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun submit() {
        busy = true
        env.scope.launch {
            env.c.auth.login(id, pw).ui(env) { u ->
                if (u.role != role) env.toast("Akun ini terdaftar sebagai ${roleLabel(u.role)}, bukan ${roleLabel(role)}")
                else {
                    env.session.signIn(u)
                    env.nav.navigate(homeRoute(u.role)) { popUpTo(env.nav.graph.id) { inclusive = true } }
                }
            }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()) {
        HeroHeader("Selamat Datang 👋", "Masuk untuk melanjutkan petualanganmu")
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Masuk sebagai", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UserRole.values().forEach { r -> Choice(roleLabel(r), role == r, { role = r }) }
            }
            Field("Email / Username / No. HP", id, { id = it })
            Field("Kata Sandi", pw, { pw = it }, password = true)
            PButton(if (busy) "Memproses…" else "Masuk Sekarang", { submit() }, Modifier.fillMaxWidth(), enabled = !busy && id.isNotBlank() && pw.isNotBlank())
            PCard(color = Mint) {
                Text("Akun demo (kata sandi: password123)", fontWeight = FontWeight.Bold, color = PineDark)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(UserRole.WISATAWAN to "rian", UserRole.MITRA_UMKM to "bunanik", UserRole.PETUGAS_HUB to "hubpwt").forEach { (r, u) ->
                        AssistChip(onClick = { role = r; id = u; pw = "password123" }, label = { Text(u) })
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("Belum punya akun?", color = Muted)
                TextButton(onClick = { env.nav.navigate("register") }) { Text("Daftar Sekarang", fontWeight = FontWeight.Bold) }
            }
            TextButton(onClick = { env.session.guest = true; env.nav.navigate("home") }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Jelajah sebagai Tamu")
            }
        }
    }
}

// ================================================================ REGISTER
@Composable
fun RegisterScreen() {
    val env = LocalApp.current
    var role by remember { mutableStateOf(UserRole.WISATAWAN) }
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var business by remember { mutableStateOf("") }
    var allergens by remember { mutableStateOf(setOf<String>()) }
    var busy by remember { mutableStateOf(false) }

    fun submit() {
        if (role == UserRole.MITRA_UMKM && business.isBlank()) { env.toast("Nama usaha wajib diisi"); return }
        busy = true
        env.scope.launch {
            env.c.auth.register(role, name, username, email, phone, pw, allergens.joinToString(",")).ui(env) { u ->
                env.scope.launch {
                    if (role == UserRole.MITRA_UMKM) env.c.catalog.addPartner(u.id, business)
                    env.session.signIn(u)
                    env.toast("Akun berhasil dibuat. Selamat datang, ${u.fullName.substringBefore(' ')}!")
                    env.nav.navigate(homeRoute(u.role)) { popUpTo(env.nav.graph.id) { inclusive = true } }
                }
            }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()) {
        HeroHeader("Buat Akun Baru", "Gratis & cuma semenit", onBack = { env.nav.popBackStack() })
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Daftar sebagai", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(UserRole.WISATAWAN, UserRole.MITRA_UMKM).forEach { r -> Choice(roleLabel(r), role == r, { role = r }) }
            }
            Field("Nama Lengkap", name, { name = it })
            Field("Username", username, { username = it.trim() })
            Field("Email", email, { email = it.trim() }, keyboard = KeyboardType.Email)
            Field("No. HP (08xx…)", phone, { phone = it }, keyboard = KeyboardType.Phone)
            Field("Kata Sandi (min. 8 karakter)", pw, { pw = it }, password = true)
            if (role == UserRole.MITRA_UMKM) Field("Nama Usaha / Toko", business, { business = it })
            if (role == UserRole.WISATAWAN) {
                Text("Alergi makanan (opsional)", fontWeight = FontWeight.SemiBold)
                Text("Rekomendasi oleh-oleh akan menyaring produk yang mengandung ini.", color = Muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("kacang", "kedelai", "susu", "gluten").forEach { a ->
                        Choice(a.replaceFirstChar { it.uppercase() }, a in allergens, { allergens = if (a in allergens) allergens - a else allergens + a })
                    }
                }
            }
            PButton(if (busy) "Memproses…" else "Daftar", { submit() }, Modifier.fillMaxWidth(), enabled = !busy)
        }
    }
}

// ================================================================ HOME (tab)
@Composable
fun HomeScreen() {
    val env = LocalApp.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                listOf(
                    Triple("Beranda", Icons.Filled.Home, 0), Triple("Oleh-Oleh", Icons.Filled.ShoppingCart, 1),
                    Triple("Trip", Icons.Filled.Group, 2), Triple("Profil", Icons.Filled.Person, 3)
                ).forEach { (label, icon, i) ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i }, icon = { Icon(icon, label) }, label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Color.White, indicatorColor = Pine, selectedTextColor = Pine)
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> DashboardTab { tab = it }
                1 -> ShopTab()
                2 -> if (env.session.loggedIn) TripListTab() else LoginPrompt("Masuk untuk membuat grup trip dan patungan")
                else -> if (env.session.loggedIn) ProfileTab() else LoginPrompt("Masuk untuk melihat profil dan riwayat")
            }
        }
    }
}

// ================================================================ DASHBOARD
fun styleEmoji(s: TravelStyle) = when (s) {
    TravelStyle.ALAM_RELAKSASI -> "🌲"
    TravelStyle.PETUALANGAN -> "🧗"
    TravelStyle.EDUKASI_KULINER -> "🏛️"
}

@Composable
fun DashboardTab(openTab: (Int) -> Unit) {
    val env = LocalApp.current
    val me by rememberMe(env)
    val dests by remember { env.c.catalog.destinations() }.collectAsState(emptyList())
    val products by remember { env.c.catalog.products() }.collectAsState(emptyList())
    val ready by remember(env.uid) { env.c.orders.myOrders(env.uid, OrderStatus.READY_PICKUP) }.collectAsState(emptyList())
    var topUp by remember { mutableStateOf(false) }
    if (topUp) TopUpDialog { topUp = false }

    fun guard(action: () -> Unit) { if (env.session.loggedIn) action() else { env.toast("Masuk dulu ya"); env.nav.navigate("login") } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        HeroHeader(
            title = "Halo, ${me?.fullName?.substringBefore(' ') ?: "Tamu"}! 👋",
            subtitle = "Mau liburan ke mana hari ini?"
        ) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().background(Color.White.copy(alpha = .15f), RoundedCornerShape(18.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (me != null) {
                    Column(Modifier.weight(1f)) {
                        Text("Saldo Eco-Wallet", color = Color.White.copy(alpha = .8f), fontSize = 12.sp)
                        Text(rupiah(me!!.walletBalance), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                        Text("🌿 ${me!!.ecoPoints} Eco-Points", color = Color.White.copy(alpha = .9f), fontSize = 12.sp)
                    }
                    Button(onClick = { topUp = true }, colors = ButtonDefaults.buttonColors(containerColor = Sun, contentColor = Ink)) { Text("Top Up", fontWeight = FontWeight.Bold) }
                } else {
                    Text("Masuk untuk memakai Eco-Wallet & Eco-Points", color = Color.White, modifier = Modifier.weight(1f))
                    Button(onClick = { env.nav.navigate("login") }, colors = ButtonDefaults.buttonColors(containerColor = Sun, contentColor = Ink)) { Text("Masuk") }
                }
            }
        }

        Column(Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    "💰" to "Smart\nBudget", "🧾" to "Split\nBill", "🎁" to "Oleh-\nOleh", "📦" to "Pickup\nHub"
                ).forEachIndexed { i, (emoji, label) ->
                    PCard(Modifier.weight(1f), onClick = {
                        when (i) {
                            0 -> guard { env.nav.navigate("budget") }
                            1 -> openTab(2)
                            2 -> openTab(1)
                            else -> guard { env.nav.navigate("orders") }
                        }
                    }) {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(emoji, fontSize = 26.sp)
                            Text(label, fontSize = 12.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, lineHeight = 14.sp)
                        }
                    }
                }
            }

            if (ready.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                PCard(color = Pine, onClick = { env.nav.navigate("order/${ready.first().id}") }) {
                    Text("🎫 ${ready.size} Pass siap digunakan", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Ketuk untuk menampilkan QR ${ready.first().code}", color = Color.White.copy(alpha = .85f), fontSize = 13.sp)
                }
            }

            products.firstOrNull { it.bestSeller }?.let { p ->
                Spacer(Modifier.height(12.dp))
                PCard(color = Color(0xFFFFF3DC), onClick = { openTab(1) }) {
                    Pill("BEST SELLER UMKM", bg = Sun, fg = Ink)
                    Spacer(Modifier.height(6.dp))
                    Text(p.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("${p.variant} • ${rupiah(p.price)}", color = Muted, fontSize = 13.sp)
                    Text("Pre-order sekarang, ambil di loker sebelum pulang →", color = Pine, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }

            SectionTitle("Destinasi Populer")
            if (dests.isEmpty()) EmptyState("🌲", "Belum ada destinasi")
            dests.forEach { d ->
                PCard(Modifier.padding(bottom = 10.dp), onClick = { env.nav.navigate("destination/${d.id}") }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(72.dp).background(Brush.linearGradient(listOf(Mint, Color(0xFFBFE3CC))), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                            Text(styleEmoji(d.style), fontSize = 32.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d.name, fontWeight = FontWeight.Bold)
                            Text(d.area, color = Muted, fontSize = 12.sp)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (d.rating > 0) Text("⭐ ${d.rating}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                if (d.ecoCertified) Pill("ECO")
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(rupiah(d.ticketPrice), fontWeight = FontWeight.ExtraBold, color = Pine)
                            Text("/orang", color = Muted, fontSize = 11.sp)
                        }
                    }
                }
            }

            PCard(color = Mint) {
                Text("🌱 Tips Eco-Traveler", fontWeight = FontWeight.Bold, color = PineDark)
                Text("Bawa botol minum sendiri dan belanja oleh-oleh dari UMKM lokal. Kamu dapat Eco-Points untuk tiap transaksi!", color = PineDark, fontSize = 13.sp)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
