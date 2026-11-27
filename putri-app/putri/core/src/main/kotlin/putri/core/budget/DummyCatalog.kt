package putri.core.budget

/** Data dummy untuk demo. Nanti diganti dari API / database (bagian CRUD). */
object DummyCatalog {
    val destinations = listOf(
        Destination("d1", "Hutan Pinus Limpakuwus", TravelStyle.ALAM_RELAKSASI, 15_000),
        Destination("d2", "Curug Cipendok", TravelStyle.ALAM_RELAKSASI, 10_000),
        Destination("d3", "Telaga Sunyi", TravelStyle.ALAM_RELAKSASI, 12_000),
        Destination("d4", "Baturraden Adventure Forest", TravelStyle.PETUALANGAN, 35_000),
        Destination("d5", "Jeep Tour Lereng Slamet", TravelStyle.PETUALANGAN, 60_000),
        Destination("d6", "Pancuran Pitu", TravelStyle.ALAM_RELAKSASI, 10_000),
        Destination("d7", "Kebun Raya Baturraden", TravelStyle.EDUKASI_KULINER, 20_000),
        Destination("d8", "Museum BRI Purwokerto", TravelStyle.EDUKASI_KULINER, 5_000),
        Destination("d9", "Sentra Getuk Goreng Sokaraja", TravelStyle.EDUKASI_KULINER, 0)
    )
    val meals = listOf(
        Meal("m1", "Soto Sokaraja", 22_000),
        Meal("m2", "Paket Mendoan Hangat", 25_000),
        Meal("m3", "Nasi Liwet Kedai Pinus", 30_000),
        Meal("m4", "Ayam Goreng Kampung Lereng", 40_000),
        Meal("m5", "Sate Ayam Khas Purwokerto", 35_000)
    )
    val souvenirs = listOf(
        Souvenir("s1", "Tempe Mendoan Mentah (isi 20)", "Toko Bu Nanik Sawangan", 35_000),
        Souvenir("s2", "Getuk Goreng Sokaraja 500gr", "Sentra H. Tohirin", 40_000),
        Souvenir("s3", "Keripik Tempe Bawang 250gr", "Banyumas Berdaya", 25_000),
        Souvenir("s4", "Dawet Ayu Instan", "UMKM Banyumas", 30_000),
        Souvenir("s5", "Gula Kelapa Premium", "UMKM Banyumas", 20_000)
    )
}
