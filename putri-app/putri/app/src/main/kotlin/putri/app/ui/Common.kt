package putri.app.ui

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

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------- tema
val Pine = Color(0xFF1F6F4A)
val PineDark = Color(0xFF0F3D2B)
val PineLight = Color(0xFF2E8B5E)
val Mint = Color(0xFFE3F4EA)
val Sun = Color(0xFFF2A93B)
val Cream = Color(0xFFF6F8F3)
val Ink = Color(0xFF14231C)
val Danger = Color(0xFFC0392B)
val Muted = Color(0xFF6B7C73)
val OkGreen = Color(0xFF2E9E5B)
val Warn = Color(0xFFD98A00)

@Composable
fun PutriTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Pine, onPrimary = Color.White, primaryContainer = Mint, onPrimaryContainer = PineDark,
            secondary = Sun, onSecondary = Ink, background = Cream, onBackground = Ink,
            surface = Color.White, onSurface = Ink, surfaceVariant = Color(0xFFEAF0EA), onSurfaceVariant = Muted,
            error = Danger, outline = Color(0xFFC7D3CB)
        ),
        shapes = Shapes(
            small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp)
        ),
        content = content
    )
}

val heroBrush = Brush.verticalGradient(listOf(PineDark, Pine, PineLight))

// ---------------------------------------------------------------- format
fun rupiah(v: Long): String =
    (if (v < 0) "-Rp " else "Rp ") + NumberFormat.getNumberInstance(Locale("id", "ID")).format(kotlin.math.abs(v))

fun fmtDate(ms: Long): String = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")).format(Date(ms))
fun fmtShort(ms: Long): String = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID")).format(Date(ms))

/** Epoch ms untuk jam "HH:mm" pada hari ini (+dayOffset). */
fun timeAt(hhmm: String, dayOffset: Int): Long {
    val (h, m) = hhmm.split(":").map { it.toInt() }
    return Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, dayOffset)
        set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

fun initials(name: String) = name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }

@Composable
fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    return now
}

fun <T> Result<T>.ui(env: AppEnv, success: String? = null, onOk: (T) -> Unit = {}) {
    onSuccess { if (success != null) env.toast(success); onOk(it) }
    onFailure { env.toast(it.message ?: "Terjadi kesalahan") }
}

@Composable
fun rememberMe(env: AppEnv): State<UserEntity?> {
    val id = env.session.userId
    val flow = remember(id) { if (id == null) flowOf(null) else env.c.auth.observe(id) }
    return flow.collectAsState(initial = null)
}

// ---------------------------------------------------------------- komponen
@Composable
fun HeroHeader(
    title: String, subtitle: String? = null, onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit = {}
) {
    Column(
        Modifier.fillMaxWidth()
            .background(heroBrush, RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = Color.White) }
            }
            Column(Modifier.weight(1f).padding(start = if (onBack == null) 4.dp else 0.dp)) {
                Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (subtitle != null) Text(subtitle, color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.bodySmall)
            }
            actions()
        }
        content()
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
fun PCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, color: Color = Color.White, content: @Composable ColumnScope.() -> Unit) {
    val colors = CardDefaults.cardColors(containerColor = color)
    val elev = CardDefaults.cardElevation(defaultElevation = 2.dp)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, colors = colors, elevation = elev, shape = MaterialTheme.shapes.medium) {
            Column(Modifier.padding(16.dp), content = content)
        }
    } else {
        Card(modifier = modifier, colors = colors, elevation = elev, shape = MaterialTheme.shapes.medium) {
            Column(Modifier.padding(16.dp), content = content)
        }
    }
}

@Composable
fun PButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, accent: Boolean = false, outlined: Boolean = false) {
    val shape = RoundedCornerShape(16.dp)
    val h = Modifier.heightIn(min = 52.dp)
    when {
        outlined -> OutlinedButton(onClick, modifier.then(h), enabled = enabled, shape = shape) { Text(text, fontWeight = FontWeight.SemiBold) }
        accent -> Button(onClick, modifier.then(h), enabled = enabled, shape = shape,
            colors = ButtonDefaults.buttonColors(containerColor = Sun, contentColor = Ink)) { Text(text, fontWeight = FontWeight.Bold) }
        else -> Button(onClick, modifier.then(h), enabled = enabled, shape = shape) { Text(text, fontWeight = FontWeight.Bold) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Choice(text: String, selected: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
    FilterChip(
        selected = selected, onClick = onClick, enabled = enabled,
        label = { Text(text) },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Pine, selectedLabelColor = Color.White)
    )
}

@Composable
fun Pill(text: String, bg: Color = Mint, fg: Color = PineDark) {
    Text(
        text, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.background(bg, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
fun Field(
    label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text, password: Boolean = false, singleLine: Boolean = true, suffix: String? = null
) {
    var show by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        modifier = modifier.fillMaxWidth(), singleLine = singleLine, shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
        visualTransformation = if (password && !show) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (password) { { TextButton(onClick = { show = !show }) { Text(if (show) "Sembunyi" else "Lihat", fontSize = 12.sp) } } } else null,
        suffix = if (suffix != null) { { Text(suffix) } } else null
    )
}

@Composable
fun FormDialog(
    title: String, onDismiss: () -> Unit, confirmText: String = "Simpan", onConfirm: () -> Unit,
    confirmEnabled: Boolean = true, content: @Composable ColumnScope.() -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp), content = content) },
        confirmButton = { TextButton(onClick = onConfirm, enabled = confirmEnabled) { Text(confirmText, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirmText: String = "Ya", onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title, fontWeight = FontWeight.Bold) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmText, color = Danger, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
fun EmptyState(emoji: String, text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 44.sp)
        Spacer(Modifier.height(8.dp))
        Text(text, color = Muted, textAlign = TextAlign.Center)
    }
}

@Composable
fun SectionTitle(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction) { Text(action, color = Pine) }
    }
}

@Composable
fun Stepper(value: Int, onChange: (Int) -> Unit, min: Int = 0, max: Int = 99) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = { if (value > min) onChange(value - 1) }, modifier = Modifier.size(34.dp)) { Text("−", fontWeight = FontWeight.Bold) }
        Text("$value", Modifier.widthIn(min = 32.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
        FilledTonalIconButton(onClick = { if (value < max) onChange(value + 1) }, modifier = Modifier.size(34.dp)) { Text("+", fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun LabelValue(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = Muted, modifier = Modifier.weight(1f))
        Text(value, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
fun QrImage(text: String, size: Dp = 220.dp) {
    val bmp = remember(text) {
        val n = 512
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, n, n)
        val px = IntArray(n * n) { i -> if (m[i % n, i / n]) 0xFF0F3D2B.toInt() else 0xFFFFFFFF.toInt() }
        Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    Image(bitmap = bmp, contentDescription = "Kode QR", modifier = Modifier.size(size).clip(RoundedCornerShape(12.dp)))
}

@Composable
fun statusColor(s: OrderStatus): Pair<Color, String> = when (s) {
    OrderStatus.WAITING_PAYMENT -> Warn to "Menunggu Pembayaran"
    OrderStatus.PAID -> Pine to "Dibayar • Disiapkan UMKM"
    OrderStatus.READY_PICKUP -> OkGreen to "Siap Diambil"
    OrderStatus.COMPLETED -> Muted to "Selesai"
    OrderStatus.EXPIRED -> Danger to "Kedaluwarsa"
    OrderStatus.CANCELLED -> Danger to "Dibatalkan"
}

@Composable
fun LoginPrompt(text: String = "Masuk dulu untuk memakai fitur ini") {
    val env = LocalApp.current
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("🔐", fontSize = 48.sp)
        Spacer(Modifier.height(8.dp))
        Text(text, textAlign = TextAlign.Center, color = Muted)
        Spacer(Modifier.height(16.dp))
        PButton("Masuk / Daftar", { env.nav.navigate("login") })
    }
}
