package putri.app

import android.content.SharedPreferences
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavHostController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import putri.data.PutriContainer
import putri.data.entity.UserEntity
import putri.data.entity.UserRole

/** Sesi login (disimpan di SharedPreferences) + status onboarding + mode tamu. */
class Session(private val prefs: SharedPreferences) {
    var userId by mutableStateOf(prefs.getLong("uid", -1L).takeIf { it > 0 })
        private set
    var role by mutableStateOf(prefs.getString("role", null)?.let { r -> runCatching { UserRole.valueOf(r) }.getOrNull() })
        private set
    var guest by mutableStateOf(false)
    var onboarded by mutableStateOf(prefs.getBoolean("onboarded", false))
        private set

    val loggedIn get() = userId != null

    fun signIn(u: UserEntity) {
        userId = u.id; role = u.role; guest = false
        prefs.edit().putLong("uid", u.id).putString("role", u.role.name).apply()
    }

    fun signOut() {
        userId = null; role = null; guest = false
        prefs.edit().remove("uid").remove("role").apply()
    }

    fun finishOnboarding() {
        onboarded = true
        prefs.edit().putBoolean("onboarded", true).apply()
    }
}

/** Semua yang dibutuhkan layar: repository, sesi, snackbar, navigasi. */
class AppEnv(
    val c: PutriContainer,
    val session: Session,
    val snack: androidx.compose.material3.SnackbarHostState,
    val scope: CoroutineScope,
    val nav: NavHostController
) {
    fun toast(msg: String) {
        scope.launch { snack.currentSnackbarData?.dismiss(); snack.showSnackbar(msg) }
    }

    /** Id pengguna yang sedang login; layar yang butuh login sudah dijaga sebelumnya. */
    val uid: Long get() = session.userId ?: -1L
}

val LocalApp = staticCompositionLocalOf<AppEnv> { error("AppEnv belum disediakan") }

fun homeRoute(role: UserRole?) = when (role) {
    UserRole.MITRA_UMKM -> "partner"
    UserRole.PETUGAS_HUB -> "officer"
    else -> "home"
}
