package com.schill.whiskeyvault

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage

// Dessa imports är de som saknades för dina röda markeringar!
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextFieldDefaults

private val FLAVOR_MAP = mapOf(
    "Sweet Notes" to listOf("Vanilla", "Butterscotch", "Coconut", "Roasted Nuts", "Caramel", "Honey", "Toffee"),
    "Fruity Notes" to listOf("Apple", "Pear", "Orange Peel", "Lemon", "Mango", "Banana"),
    "Spicy Notes" to listOf("Cinnamon", "Nutmeg", "Clove", "Pepper", "Ginger"),
    "Smoky/Peaty" to listOf("Peat Smoke", "Campfire", "Tar", "Iodine", "Ash"),
    "Woody/Nutty" to listOf("Oak", "Hazelnut", "Walnut", "Tobacco", "Dark Chocolate"),
    "Floral/Grassy" to listOf("Heather", "Dried Grass", "Herbs", "Floral")
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FullAddDialog(
    existing: Whiskey?,
    img: String?,
    onDismiss: () -> Unit,
    onSave: (Whiskey) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var abv by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("") }
    var volume by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var aiStores by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Unopened") }
    var isWishlist by remember { mutableStateOf(false) }
    val selectedFlavors = remember { mutableStateListOf<String>() }
    var openDropdown by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(existing) {
        existing?.let {
            name = it.name; country = it.country; price = it.price; abv = it.abv; type = it.type; volume = it.volume
            status = it.status; isWishlist = it.isWishlist

            if (it.notes.startsWith("Suggested stores:")) {
                val split = it.notes.split("\n\n")
                aiStores = split[0].replace("Suggested stores:", "").trim()
                notes = if (split.size > 1) split[1] else ""
            } else {
                notes = it.notes; aiStores = ""
            }

            selectedFlavors.clear()
            if (it.flavorProfile.isNotBlank()) {
                selectedFlavors.addAll(it.flavorProfile.split(",").map { it.trim() })
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF121212)) {
            Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState()).statusBarsPadding()) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("VAULT ENTRY", color = Color(0xFFFFBF00), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = Color.White) }
                }

                AsyncImage(
                    model = img ?: existing?.imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(180.dp).padding(vertical = 16.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                    Checkbox(checked = isWishlist, onCheckedChange = { isWishlist = it }, colors = CheckboxDefaults.colors(checkedColor = Color(0xFFFFBF00)))
                    Text("Add to Wishlist", color = Color.White)
                }

                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), textStyle = TextStyle(color = Color.White))

                // --- SMAKER ---
                Text("FLAVOR PROFILES", color = Color(0xFFFFBF00), modifier = Modifier.padding(top = 24.dp, bottom = 8.dp), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                FLAVOR_MAP.forEach { (category, flavors) ->
                    Box(Modifier.padding(vertical = 4.dp)) {
                        OutlinedButton(onClick = { openDropdown = if (openDropdown == category) null else category }, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                                Text(category, color = Color.White)
                                Icon(Icons.Default.ArrowDropDown, null, tint = Color(0xFFFFBF00))
                            }
                        }
                        DropdownMenu(expanded = openDropdown == category, onDismissRequest = { openDropdown = null }, modifier = Modifier.fillMaxWidth(0.85f).background(Color(0xFF2D2D2D))) {
                            flavors.forEach { flavor ->
                                DropdownMenuItem(
                                    text = { Row { Checkbox(selectedFlavors.contains(flavor), null); Text(flavor, color = Color.White) } },
                                    onClick = { if (selectedFlavors.contains(flavor)) selectedFlavors.remove(flavor) else selectedFlavors.add(flavor) }
                                )
                            }
                        }
                    }
                }

                // --- SMAK-CHIPS (Här var det rött förut!) ---
                FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    selectedFlavors.forEach {
                        SuggestionChip(onClick = { }, label = { Text(it, fontSize = 10.sp) })
                    }
                }

                Row(Modifier.fillMaxWidth().padding(top = 16.dp), Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Price") }, modifier = Modifier.weight(1f), textStyle = TextStyle(color = Color.White))
                    OutlinedTextField(value = abv, onValueChange = { abv = it }, label = { Text("ABV %") }, modifier = Modifier.weight(1f), textStyle = TextStyle(color = Color.White))
                }

                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("MY NOTES") }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp), textStyle = TextStyle(color = Color.White), minLines = 3)

                Button(
                    onClick = {
                        val finalNotes = if (aiStores.isNotBlank()) "Suggested stores: $aiStores\n\n$notes" else notes
                        onSave(
                            Whiskey(
                                id = existing?.id ?: 0,
                                name = name,
                                country = country,
                                price = price,
                                abv = abv,
                                type = type,
                                volume = volume,
                                flavorProfile = selectedFlavors.joinToString(","),
                                rating = existing?.rating ?: 5,
                                imageUrl = img ?: existing?.imageUrl,
                                notes = finalNotes.trim(),
                                status = status,
                                isWishlist = isWishlist
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 85.dp).height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFBF00))
                ) {
                    Text("SAVE TO VAULT", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}