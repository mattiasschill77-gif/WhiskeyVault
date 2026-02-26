package com.schill.whiskeyvault

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "whiskey_table")
data class Whiskey(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String = "",
    val country: String = "",
    val type: String = "",
    val volume: String = "",
    val price: String = "",
    val rating: Int = 0,      // Säkerställ att vi har ett startvärde på 0
    val imageUrl: String? = null,
    val flavorProfile: String = "",
    val status: String = "Unopened",
    val abv: String = "",
    val notes: String = "",
    val retailer: String = "Not specified",
    val isWishlist: Boolean = false
) {
    // Hjälpfunktion för att få en snygg lista i UI:t
    val flavorList: List<String>
        get() = if (flavorProfile.isBlank()) emptyList() else flavorProfile.split(",").map { it.trim() }

    // Används för summeringen i StatsCard
    val numericPrice: Long
        get() = price.filter { it.isDigit() }.toLongOrNull() ?: 0L
}