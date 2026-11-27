package putri.core.pickup

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

enum class PassStatus { READY, EXPIRED, USED, INVALID, WRONG_LOCKER }

data class PickupPass(
    val orderId: String,
    val lockerId: String,
    val expiresAt: Long,   // epoch millis
    val token: String,     // isi QR
    val backupCode: String // kode manual kalau QR gagal discan
)

/**
 * Token QR ditandatangani HMAC-SHA256 supaya tidak bisa dipalsukan.
 * Format token: orderId|lockerId|expiresAt|signature
 * Catatan: di produksi kunci rahasia harus ada di server, bukan di app.
 */
class PickupPassService(private val secret: ByteArray) {

    private val used = mutableSetOf<String>()

    fun issue(orderId: String, lockerId: String, expiresAt: Long): PickupPass {
        val payload = "$orderId|$lockerId|$expiresAt"
        val sig = sign(payload)
        val backup = "BTR-" + sig.take(8).uppercase().chunked(4).joinToString("-")
        return PickupPass(orderId, lockerId, expiresAt, "$payload|$sig", backup)
    }

    fun validate(token: String, scannedLockerId: String, now: Long): PassStatus {
        val parts = token.split("|")
        if (parts.size != 4) return PassStatus.INVALID
        val payload = parts.take(3).joinToString("|")
        if (!constantTimeEquals(sign(payload), parts[3])) return PassStatus.INVALID
        val expiresAt = parts[2].toLongOrNull() ?: return PassStatus.INVALID
        if (parts[1] != scannedLockerId) return PassStatus.WRONG_LOCKER
        if (used.contains(parts[0])) return PassStatus.USED
        if (now > expiresAt) return PassStatus.EXPIRED
        return PassStatus.READY
    }

    /** Dipanggil saat loker benar-benar dibuka. */
    fun redeem(token: String, scannedLockerId: String, now: Long): PassStatus {
        val st = validate(token, scannedLockerId, now)
        if (st == PassStatus.READY) used += token.substringBefore("|")
        return st
    }

    /** Sisa waktu pengambilan untuk countdown di E-Tiket (0 kalau sudah lewat). */
    fun remainingMillis(pass: PickupPass, now: Long) = (pass.expiresAt - now).coerceAtLeast(0)

    private fun sign(payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        return mac.doFinal(payload.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var r = 0
        for (i in a.indices) r = r or (a[i].code xor b[i].code)
        return r == 0
    }
}

fun formatCountdown(millis: Long): String {
    val s = millis / 1000
    return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}
