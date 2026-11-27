package putri.data.repo

import androidx.room.withTransaction
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import putri.data.PutriDatabase
import putri.data.entity.UserEntity
import putri.data.entity.UserRole

internal object PasswordHasher {
    private const val ITER = 120_000
    fun newSalt(): String = ByteArray(16).also { SecureRandom().nextBytes(it) }.toHex()
    fun hash(password: String, saltHex: String): String {
        val spec = PBEKeySpec(password.toCharArray(), saltHex.hexToBytes(), ITER, 160)
        // SHA1 dipilih supaya jalan di minSdk 24
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded.toHex()
    }
    fun matches(password: String, saltHex: String, expected: String) =
        MessageDigest.isEqual(hash(password, saltHex).toByteArray(), expected.toByteArray())
    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    private fun String.hexToBytes() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

/** Registrasi, login (email / username / no HP), profil, ganti sandi, hapus akun. */
class AuthRepository(private val db: PutriDatabase, private val clock: Clock = systemClock) {
    private val users = db.userDao()

    private val emailRe = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    private val phoneRe = Regex("^(\\+62|62|0)8[0-9]{8,12}$")

    suspend fun register(
        role: UserRole, fullName: String, username: String, email: String,
        phone: String, password: String, allergens: String = ""
    ): Result<UserEntity> = runCatching {
        val ph = phone.replace(" ", "").replace("-", "")
        require(fullName.isNotBlank()) { "Nama wajib diisi" }
        require(username.length >= 4 && username.all { it.isLetterOrDigit() || it == '_' || it == '.' }) {
            "Username minimal 4 karakter (huruf, angka, _ atau .)"
        }
        require(emailRe.matches(email.trim())) { "Format email tidak valid" }
        require(phoneRe.matches(ph)) { "Nomor HP tidak valid (contoh 0812xxxxxxx)" }
        require(password.length >= 8) { "Kata sandi minimal 8 karakter" }
        db.withTransaction {
            require(users.countConflicts(username.trim(), email.trim()) == 0) { "Username atau email sudah terdaftar" }
            val salt = PasswordHasher.newSalt()
            val id = users.insert(
                UserEntity(
                    role = role, fullName = fullName.trim(), username = username.trim(),
                    email = email.trim().lowercase(), phone = ph,
                    passwordHash = PasswordHasher.hash(password, salt), salt = salt,
                    allergens = allergens, createdAt = clock()
                )
            )
            users.setPublicCode(id, "#BMS-${9000 + id}")
            users.byId(id)!!
        }
    }

    suspend fun login(identifier: String, password: String): Result<UserEntity> = runCatching {
        val u = users.byIdentifier(identifier.trim())
        // pesan sengaja sama agar tidak membocorkan akun mana yang ada
        require(u != null && PasswordHasher.matches(password, u.salt, u.passwordHash)) { "Akun atau kata sandi salah" }
        u
    }

    suspend fun updateProfile(userId: Long, fullName: String, email: String, phone: String, allergens: String): Result<UserEntity> =
        runCatching {
            val ph = phone.replace(" ", "").replace("-", "")
            require(fullName.isNotBlank()) { "Nama wajib diisi" }
            require(emailRe.matches(email.trim())) { "Format email tidak valid" }
            require(phoneRe.matches(ph)) { "Nomor HP tidak valid" }
            db.withTransaction {
                val u = users.byId(userId) ?: error("Akun tidak ditemukan")
                val other = users.byIdentifier(email.trim())
                require(other == null || other.id == userId) { "Email sudah dipakai akun lain" }
                users.update(u.copy(fullName = fullName.trim(), email = email.trim().lowercase(), phone = ph, allergens = allergens))
                users.byId(userId)!!
            }
        }

    suspend fun changePassword(userId: Long, old: String, new: String): Result<Unit> = runCatching {
        require(new.length >= 8) { "Kata sandi baru minimal 8 karakter" }
        db.withTransaction {
            val u = users.byId(userId) ?: error("Akun tidak ditemukan")
            require(PasswordHasher.matches(old, u.salt, u.passwordHash)) { "Kata sandi lama salah" }
            val salt = PasswordHasher.newSalt()
            users.update(u.copy(salt = salt, passwordHash = PasswordHasher.hash(new, salt)))
        }
    }

    /** Hapus akun; data terkait (keranjang, pesanan, keanggotaan grup) ikut terhapus lewat FK cascade. */
    suspend fun deleteAccount(userId: Long, password: String): Result<Unit> = runCatching {
        val u = users.byId(userId) ?: error("Akun tidak ditemukan")
        require(PasswordHasher.matches(password, u.salt, u.passwordHash)) { "Kata sandi salah" }
        users.delete(u)
    }

    fun observe(userId: Long) = users.observe(userId)
}
