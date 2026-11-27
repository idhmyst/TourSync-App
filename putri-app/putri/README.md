# PassGo Banyumas Eco-Pass (TourSync) — aplikasi Android

Bahasa: Kotlin • UI: Jetpack Compose (Material 3) • Database: Room (SQLite) • Package: **`putri`**
(applicationId Android wajib minimal 2 segmen, jadi dipakai `putri.app`; semua package Kotlin tetap `putri.*`.)

## Cara menjalankan
1. Buka folder ini di **Android Studio (Koala / 2024.1+)**, tunggu Gradle sync (butuh internet).
2. Pilih modul **app**, jalankan di emulator / HP (Android 7.0+).
3. Akun demo (kata sandi `password123`):
   | Peran | Username |
   |---|---|
   | Wisatawan (saldo Rp450.000, 320 poin) | `rian` |
   | Mitra UMKM (Toko Bu Nanik) | `bunanik` |
   | Petugas Hub | `hubpwt` |

Test: `./gradlew :core:test` (logika murni) • `./gradlew :data:connectedAndroidTest` (alur CRUD, butuh emulator).

## Struktur
```
core/  putri.core.*      logika murni: budget, split-bill, QR HMAC, poin    (JVM, 12 test)
data/  putri.data.*      Room: 22 tabel, DAO, repository CRUD, seed         (Android library)
app/   putri.app.*       UI Compose: 14 layar + 3 peran
```

## Layar & alur
**Wisatawan**
- Onboarding → Login / Daftar (pilih peran, alergi makanan) / Jelajah sebagai Tamu
- Beranda: saldo + poin + top up, aksi cepat, pass aktif, best seller, destinasi populer
- Detail Destinasi: info, daya tarik, pilih paket tiket, jumlah, tanggal → Pesan Pass
- Smart Budget Planner: slider anggaran, orang, durasi, gaya → paket otomatis (UMKM ≥ 10%), riwayat
- Oleh-Oleh: cari, kategori, peringatan alergi, keranjang tersimpan otomatis
- Keranjang → pilih Pickup Hub + hari ambil → Checkout (timer 15 menit, tukar Eco-Points, Eco-Wallet / Dompet Bersama / QRIS / VA)
- Pesanan & E-Tiket: QR + kode cadangan + countdown, status live, batal + refund
- Group Trip: buat grup, undang, isi kas, catat pengeluaran (rata / kustom / foto struk), status patungan (bayar via wallet, konfirmasi), arsip
- Profil: level Eco-Explorer, mutasi saldo & poin, ubah profil, ganti sandi, hapus akun, keluar

**Mitra UMKM** — CRUD produk (stok cepat +/−), antrian pesanan → pilih loker → QR terbit untuk pembeli.
**Petugas Hub** — validasi pass (QR / kode cadangan), CRUD hub & loker (status kosong/terisi/perawatan), CRUD destinasi, paket tiket, menu kuliner.

Status pesanan: `WAITING_PAYMENT → PAID → READY_PICKUP → COMPLETED` (atau `EXPIRED` / `CANCELLED` dengan pemulihan stok, saldo, poin). Tiket wisata langsung `READY_PICKUP` dengan QR gerbang.

## Catatan jujur
- **Teruji nyata:** modul `core` dikompilasi dengan kotlinc 1.9.24 dan 12/12 test lolos.
- **Belum dikompilasi:** modul `data` dan `app` (butuh Android SDK, Room, Compose yang tidak tersedia di lingkungan pembuatan). Bila ada error compile saat sync/build, laporkan teks errornya untuk diperbaiki.
- Gateway QRIS & Virtual Account **disimulasikan** (langsung sukses).
- Validasi QR memakai kunci HMAC di dalam app (demo). Produksi: validasi di server.
- Pemindai kamera belum ada; petugas menempel isi QR atau mengetik kode cadangan.
- Aturan poin (1 poin / Rp10.000, 24 poin = Rp1.000, level tiap 200 poin) adalah asumsi.
- Lewat batas ambil: pesanan hangus tanpa refund otomatis (bisa diubah di `OrderRepository.expireOverdue`).

## Membangun APK tanpa Android Studio (GitHub Actions)
1. Buat repository baru di github.com, unggah **seluruh isi folder ini** (termasuk folder tersembunyi `.github`).
2. Buka tab **Actions** → workflow **Build APK** → **Run workflow** (juga jalan otomatis tiap push).
3. Tunggu ±5-10 menit → buka run yang hijau → bagian **Artifacts** → unduh **passgo-debug-apk** → ekstrak → `app-debug.apk`.
4. Salin ke HP, izinkan "Instal dari sumber tidak dikenal", lalu instal.
Jika run merah, buka log langkah "Test logika inti + build APK debug" dan kirim teks errornya.
