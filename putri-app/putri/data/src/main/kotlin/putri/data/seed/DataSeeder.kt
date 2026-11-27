package putri.data.seed

import putri.core.budget.TravelStyle
import putri.core.splitbill.ExpenseCategory
import putri.data.PutriDatabase
import putri.data.entity.*
import putri.data.repo.AuthRepository

/** Data awal sesuai desain Putri. Aman dipanggil berkali-kali (hanya mengisi saat kosong). */
class DataSeeder(private val db: PutriDatabase, private val auth: AuthRepository) {

    suspend fun seedIfEmpty() {
        if (db.userDao().count() == 0) seedUsers()
        if (db.catalogDao().destinationCount() == 0) seedCatalog()
        if (db.hubDao().hubCount() == 0) seedHubs()
    }

    private suspend fun seedUsers() {
        // kata sandi demo: password123
        val rian = auth.register(UserRole.WISATAWAN, "Rian Pratama", "rian", "rian@baturraden.id", "081249208819", "password123").getOrThrow()
        db.userDao().credit(rian.id, 450_000)
        db.userDao().addPoints(rian.id, 320)
        db.walletDao().insertPoints(EcoPointTxEntity(userId = rian.id, delta = 320, reason = "Poin awal", createdAt = System.currentTimeMillis()))
        auth.register(UserRole.MITRA_UMKM, "Bu Nanik", "bunanik", "nanik@umkm.id", "081300000001", "password123").getOrThrow()
        auth.register(UserRole.PETUGAS_HUB, "Petugas Hub PWT", "hubpwt", "hub@passgo.id", "081300000002", "password123").getOrThrow()
    }

    private suspend fun seedCatalog() {
        val c = db.catalogDao(); val p = db.productDao()
        val pinus = c.insertDestination(DestinationEntity(
            name = "Hutan Pinus Limpakuwus", area = "BATURRADEN TIMUR",
            address = "Desa Limpakuwus, Kec. Sumbang, Kab. Banyumas", style = TravelStyle.ALAM_RELAKSASI,
            description = "Hamparan pinus berusia 30+ tahun di lereng selatan Gunung Slamet dengan udara jernih beraroma getah pinus.",
            ticketPrice = 15_000, openTime = "08:00", closeTime = "17:00", altitudeMdpl = 800, avgTempC = 20,
            rating = 4.8, reviewCount = 1200, badge = "Eco-Certified",
            facilities = "Mushola,Toilet,Parkir", highlights = "Mountain Slide,Spot Jembatan Kanopi,Family Camping Ground,Kedai Mendoan Hangat"))
        c.insertTicket(TicketPackageEntity(destinationId = pinus, name = "Tiket Masuk Reguler",
            description = "Akses seluruh area, spot foto, dan asuransi Jasa Raharja.", price = 15_000))
        c.insertTicket(TicketPackageEntity(destinationId = pinus, name = "Mountain Slide (Peluncuran)",
            description = "Meluncur 100 meter di sela kanopi pinus.", price = 20_000, unitLabel = "peluncuran", popular = true))
        val telaga = c.insertDestination(DestinationEntity(
            name = "Telaga Sunyi", area = "BATURRADEN", address = "Baturraden, Banyumas", style = TravelStyle.ALAM_RELAKSASI,
            description = "Air jernih alami dan suasana hening untuk relaksasi.", ticketPrice = 15_000,
            openTime = "07:00", closeTime = "16:30", rating = 4.9, reviewCount = 800, badge = "Nature Reserve"))
        c.insertTicket(TicketPackageEntity(destinationId = telaga, name = "Tiket Masuk", price = 15_000))
        val loka = c.insertDestination(DestinationEntity(
            name = "Lokawisata Baturraden", area = "BATURRADEN", address = "Kemutug Lor, Baturraden", style = TravelStyle.EDUKASI_KULINER,
            description = "Kolam air panas belerang alami dan curug.", ticketPrice = 25_000,
            openTime = "06:30", closeTime = "18:00", rating = 4.7, reviewCount = 2100, badge = "Wellness"))
        c.insertTicket(TicketPackageEntity(destinationId = loka, name = "Tiket Terusan", price = 25_000))
        c.insertDestination(DestinationEntity(name = "Curug Cipendok", area = "CILONGOK", address = "Karangtengah, Cilongok",
            style = TravelStyle.ALAM_RELAKSASI, description = "Air terjun tinggi di tengah hutan lindung.", ticketPrice = 10_000, rating = 4.6))
        c.insertDestination(DestinationEntity(name = "Baturraden Adventure Forest", area = "BATURRADEN", address = "Baturraden, Banyumas",
            style = TravelStyle.PETUALANGAN, description = "Flying fox, ATV, dan outbound.", ticketPrice = 35_000, rating = 4.5))
        c.insertDestination(DestinationEntity(name = "Jeep Tour Lereng Slamet", area = "BATURRADEN", address = "Pos Jeep Baturraden",
            style = TravelStyle.PETUALANGAN, description = "Safari jeep menyusuri lereng Gunung Slamet.", ticketPrice = 60_000, rating = 4.7))

        listOf(
            MealEntity(name = "Soto Sokaraja", partnerName = "Warung Soto Sokaraja", pricePerPerson = 22_000),
            MealEntity(name = "Paket Mendoan Hangat", partnerName = "Kedai Mendoan Pinus", pricePerPerson = 25_000),
            MealEntity(name = "Nasi Timbel & Kopi Lereng", partnerName = "Warung Lereng Slamet", pricePerPerson = 40_000),
            MealEntity(name = "Pecel Baturraden", partnerName = "Warung Pecel Baturraden", pricePerPerson = 30_000)
        ).forEach { c.insertMeal(it) }

        val owner = db.userDao().byIdentifier("bunanik")?.id
        val nanik = p.insertPartner(PartnerEntity(name = "Toko Bu Nanik Sawangan", address = "Sawangan, Baturraden", ownerUserId = owner))
        val tohirin = p.insertPartner(PartnerEntity(name = "Sentra H. Tohirin Sokaraja", address = "Sokaraja, Banyumas"))
        val mitra = p.insertPartner(PartnerEntity(name = "Mitra UMKM Banyumas Berdaya", address = "Purwokerto"))
        p.insert(ProductEntity(partnerId = nanik, name = "Tempe Mendoan Mentah Sawangan", variant = "Isi 20 + Sambal Kecap",
            category = "Tempe & Olahan", price = 35_000, stock = 50, packaging = "Vacuum Seal (Tahan 48 Jam)", allergens = "kedelai", bestSeller = true))
        p.insert(ProductEntity(partnerId = tohirin, name = "Getuk Goreng Asli Sokaraja", variant = "Rasa Gula Kelapa (500gr)",
            category = "Getuk Sokaraja", price = 40_000, stock = 40, packaging = "Box Besek Tradisional"))
        p.insert(ProductEntity(partnerId = mitra, name = "Keripik Tempe Gurih Bawang", variant = "Renyah (Pouch 250gr)",
            category = "Tempe & Olahan", price = 25_000, stock = 60, packaging = "Pouch Higienis", allergens = "kedelai"))
        p.insert(ProductEntity(partnerId = mitra, name = "Kacang Garing Banyumas", variant = "Gurih Alami (250gr)",
            category = "Kue & Camilan", price = 20_000, stock = 30, packaging = "Pouch", allergens = "kacang"))
        p.insert(ProductEntity(partnerId = mitra, name = "Nopia Mini", variant = "Isi Gula Aren (box 10)",
            category = "Kue & Camilan", price = 28_000, stock = 45, packaging = "Box Kardus"))
    }

    private suspend fun seedHubs() {
        val h = db.hubDao()
        val pwt = h.insertHub(HubEntity(name = "Loker Pintar Pintu Barat Stasiun Purwokerto",
            address = "Pintu Barat Stasiun Purwokerto (PWT), dekat ruang tunggu eksekutif & ATM",
            description = "Loker pendingin untuk oleh-oleh segar", openTime = "17:00", closeTime = "21:00", temperatureControlled = true))
        (1..8).forEach { h.insertLocker(LockerEntity(hubId = pwt, code = "LOKER-%02d".format(it))) }
        val term = h.insertHub(HubEntity(name = "Drop Point Terminal Baturraden", address = "Terminal Baturraden",
            description = "Titik titip tanpa pendingin", openTime = "08:00", closeTime = "20:00"))
        (1..3).forEach { h.insertLocker(LockerEntity(hubId = term, code = "DROP-%02d".format(it))) }
    }
}
