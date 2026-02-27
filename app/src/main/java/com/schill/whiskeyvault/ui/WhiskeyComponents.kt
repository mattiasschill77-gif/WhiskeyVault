package com.schill.whiskeyvault.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.text.NumberFormat
import java.util.*

// Importera Whiskey-modellen från huvudmappen
import com.schill.whiskeyvault.Whiskey

@Composable
fun PremiumWhiskeyCard(w: Whiskey, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(0.05f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                AsyncImage(
                    model = w.imageUrl ?: "https://via.placeholder.com/150",
                    contentDescription = null,
                    modifier = Modifier.size(70.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                if (w.isWishlist) Icon(
                    Icons.Default.Favorite, null, tint = Color.Red,
                    modifier = Modifier.size(20.dp).align(Alignment.TopEnd).padding(2.dp)
                )
            }
            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                Text(w.name, color = Color(0xFFFFBF00), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("${w.type} • ${w.abv}%", color = Color.White.copy(0.6f), fontSize = 12.sp)
                if (!w.isWishlist) {
                    val statusColor = when (w.status) {
                        "Unopened" -> Color(0xFF4CAF50)
                        "Open" -> Color(0xFFFFBF00)
                        else -> Color.Red
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(statusColor))
                        Text("  ${w.status}", color = statusColor, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun PremiumStatsCard(list: List<Whiskey>, onClick: () -> Unit) {
    val totalBottles = list.size
    val totalValue = list.sumOf { it.numericPrice }
    val formattedValue = NumberFormat.getNumberInstance(Locale("sv", "SE")).format(totalValue)

    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp).clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(0.08f)),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, Color.White.copy(0.1f))
    ) {
        Column(Modifier.padding(24.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("TOTAL VAULT", color = Color(0xFFFFBF00), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("$totalBottles", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("EST. VALUE", color = Color(0xFFFFBF00), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("$formattedValue:-", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
fun SortingAndFilterRow(
    currentFilter: String,
    onFilterChange: (String) -> Unit,
    currentSort: String,
    onSortChange: (String) -> Unit
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Wishlist", "Scotland", "USA", "Ireland", "Japan").forEach { country ->
                FilterChip(
                    selected = currentFilter == country,
                    onClick = { onFilterChange(country) },
                    label = { Text(country) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFFFBF00).copy(0.2f),
                        selectedLabelColor = Color(0xFFFFBF00)
                    )
                )
            }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf("Name", "Price", "Rating").forEach { sort ->
                Text(
                    sort,
                    color = if (currentSort == sort) Color(0xFFFFBF00) else Color.White.copy(0.6f),
                    fontSize = 12.sp,
                    fontWeight = if (currentSort == sort) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.clickable { onSortChange(sort) }
                )
            }
        }
    }
}

@Composable
fun EmptyVaultState() {
    Column(
        Modifier.fillMaxWidth().padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.WineBar, null, modifier = Modifier.size(80.dp).alpha(0.3f), tint = Color.White)
        Spacer(Modifier.height(16.dp))
        Text("Ekot i valvet...", color = Color.White.copy(0.5f), fontSize = 16.sp)
    }
}