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


// ================================================================ PROFIL (tab)
@Composable
fun ProfileTab() {
    val env = LocalApp.current
    val me by rememberMe(env)
    val orders by remember { env.c.orders.myOrders(env.uid) }.collectAsState(emptyList())
    val level by produceState(1, me?.ecoPoints) { value = env.c.wallet.ecoLevel(env.uid) }
    var dlg by remember { mutableStateOf<String?>(null) }
    val u = me ?: return

    when (dlg) {
        "edit" -> EditProfileDialog(u) { dlg = null }
        "password" -> PasswordDialog { dlg = null }
        "delete" -> DeleteAccountDialog { dlg = null }
        "topup" -> TopUpDialog { dlg = null }
        "logout" -> ConfirmDialog("Keluar dari akun?", "Kamu bisa masuk lagi kapan saja.", "Keluar", { dlg = null }) {
            env.session.signOut(); env.nav.navigate("login") { popUpTo(env.nav.graph.id) { inclusive = true } }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        HeroHeader("Profil") {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp).background(Sun, CircleShape), contentAlignment = Alignment.Center) { Text(initials(u.fullName), fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = Ink) }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(u.fullName, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                    Text("Eco-Explorer Level $level • ${u.publicCode}", color = Color.White.copy(alpha = .85f), fontSize = 12.sp)
                }
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Saldo Eco-Wallet", color = Muted, fontSize = 12.sp)
                        Text(rupiah(u.walletBalance), fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = Pine)
                        Text("🌿 ${u.ecoPoints} Eco-Points", fontSize = 12.sp)
                    }
                    Button(onClick = { dlg = "topup" }) { Text("Top Up") }
                }
                TextButton(onClick = { env.nav.navigate("wallet") }) { Text("Lihat mutasi saldo & poin →") }
            }
            PCard {
                Text("Akun", fontWeight = FontWeight.Bold)
                LabelValue("Username", u.username); LabelValue("Email", u.email); LabelValue("No. HP", u.phone)
                LabelValue("Alergi", if (u.allergenList().isEmpty()) "Tidak ada" else u.allergenList().joinToString())
                Row { TextButton(onClick = { dlg = "edit" }) { Text("Ubah Profil") }; TextButton(onClick = { dlg = "password" }) { Text("Ganti Sandi") } }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Pesanan Terbaru", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { env.nav.navigate("orders") }) { Text("Lihat semua") }
            }
            if (orders.isEmpty()) EmptyState("🎫", "Belum ada pesanan")
            orders.take(3).forEach { OrderCard(it) }

            PButton("Keluar", { dlg = "logout" }, Modifier.fillMaxWidth(), outlined = true)
            TextButton(onClick = { dlg = "delete" }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Hapus akun", color = Danger) }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun EditProfileDialog(u: UserEntity, onDismiss: () -> Unit) {
    val env = LocalApp.current
    var name by remember { mutableStateOf(u.fullName) }
    var email by remember { mutableStateOf(u.email) }
    var phone by remember { mutableStateOf(u.phone) }
    var al by remember { mutableStateOf(u.allergenList().toSet()) }
    FormDialog("Ubah Profil", onDismiss, onConfirm = {
        env.scope.launch { env.c.auth.updateProfile(u.id, name, email, phone, al.joinToString(",")).ui(env, "Profil diperbarui") { onDismiss() } }
    }) {
        Field("Nama lengkap", name, { name = it }); Field("Email", email, { email = it }, keyboard = KeyboardType.Email); Field("No. HP", phone, { phone = it }, keyboard = KeyboardType.Phone)
        Text("Alergi makanan", fontWeight = FontWeight.SemiBold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("kacang", "kedelai", "susu", "gluten").forEach { a -> Choice(a.replaceFirstChar { it.uppercase() }, a in al, { al = if (a in al) al - a else al + a }) }
        }
    }
}

@Composable
private fun PasswordDialog(onDismiss: () -> Unit) {
    val env = LocalApp.current
    var old by remember { mutableStateOf("") }; var new by remember { mutableStateOf("") }
    FormDialog("Ganti Kata Sandi", onDismiss, onConfirm = {
        env.scope.launch { env.c.auth.changePassword(env.uid, old, new).ui(env, "Kata sandi diganti") { onDismiss() } }
    }) { Field("Kata sandi lama", old, { old = it }, password = true); Field("Kata sandi baru (min. 8)", new, { new = it }, password = true) }
}

@Composable
private fun DeleteAccountDialog(onDismiss: () -> Unit) {
    val env = LocalApp.current
    var pw by remember { mutableStateOf("") }
    FormDialog("Hapus Akun Permanen", onDismiss, "Hapus", confirmEnabled = pw.isNotBlank(), onConfirm = {
        env.scope.launch {
            env.c.auth.deleteAccount(env.uid, pw).ui(env, "Akun dihapus") {
                env.session.signOut(); env.nav.navigate("login") { popUpTo(env.nav.graph.id) { inclusive = true } }
            }
        }
    }) {
        Text("Semua pesanan, keranjang, dan keanggotaan grupmu ikut terhapus. Tindakan ini tidak bisa dibatalkan.", color = Danger, fontSize = 13.sp)
        Field("Masukkan kata sandi", pw, { pw = it }, password = true)
    }
}
