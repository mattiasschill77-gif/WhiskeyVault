package com.schill.whiskeyvault

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "whiskey_table")
data class Whiskey(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val country: String,
    val type: String = "",    // NY: t.ex. Single Malt, Bourbon
    val volume: String = "",  // NY: t.ex. 70cl
    val price: String,
    val rating: Int,
    val imageUrl: String? = null,
    val flavorProfile: String = "",
    val status: String = "Unopened",
    val abv: String = "",
    val notes: String = "",
    val retailer: String = "Not specified",
    val isWishlist: Boolean = false
) {
    val flavorList: List<String>
        get() = if (flavorProfile.isBlank()) emptyList() else flavorProfile.split(",")

    val numericPrice: Int
        get() = price.filter { it.isDigit() }.toIntOrNull() ?: 0
}