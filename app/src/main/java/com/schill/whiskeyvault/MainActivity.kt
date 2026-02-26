@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
package com.schill.whiskeyvault

import android.util.Log
import android.Manifest
import android.content.*
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.schill.whiskeyvault.ui.theme.WhiskeyVaultTheme
import kotlinx.coroutines.launch
import java.io.File
import java.text.NumberFormat
import java.util.Locale

// Smak-karta för dropdowns
private val FLAVOR_MAP = mapOf(
    "Sweet Notes" to listOf("Vanilla", "Butterscotch", "Coconut", "Roasted Nuts", "Caramel", "Honey", "Toffee"),
    "Fruity Notes" to listOf("Apple", "Pear", "Orange Peel", "Lemon", "Mango", "Banana"),
    "Spicy Notes" to listOf("Cinnamon", "Nutmeg", "Clove", "Pepper", "Ginger"),
    "Smoky/Peaty" to listOf("Peat Smoke", "Campfire", "Tar", "Iodine", "Ash"),
    "Woody/Nutty" to listOf("Oak", "Hazelnut", "Walnut", "Tobacco", "Dark Chocolate"),
    "Floral/Grassy" to listOf("Heather", "Dried Grass", "Herbs", "Floral")
)

class MainActivity : ComponentActivity() {
    private var currentPhotoPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val database = WhiskeyDatabase.getDatabase(this)
        val whiskeyDao = database.whiskeyDao()

        setContent {
            WhiskeyVaultTheme {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()

                // --- SETTINGS & AI ---
                val aiHelper = remember { AiHelper(Secrets.GEMINI_KEY) }

                // --- DATABASE STATES ---
                val myCollection by whiskeyDao.getAllWhiskeys().collectAsState(initial = emptyList())
                var searchQuery by remember { mutableStateOf("") }
                var activeFilter by remember { mutableStateOf("All") }
                var sortOrder by remember { mutableStateOf("Name") }

                // --- UI STATES ---
                var showAddDialog by remember { mutableStateOf(false) }
                var showSettingsDialog by remember { mutableStateOf(false) }
                var showSelectionDialog by remember { mutableStateOf(false) }
                var selectedWhiskey by remember { mutableStateOf<Whiskey?>(null) }
                var editingWhiskey by remember { mutableStateOf<Whiskey?>(null) }
                var aiDraftWhiskey by remember { mutableStateOf<Whiskey?>(null) }
                var isProcessing by remember { mutableStateOf(false) }
                var capturedUrl by remember { mutableStateOf<String?>(null) }

                // --- LOGIK: FILTRERING & SORTERING ---
                val processedCollection = remember(myCollection, searchQuery, activeFilter, sortOrder) {
                    var list = myCollection.filter {
                        it.name.contains(searchQuery, true) || it.flavorProfile.contains(searchQuery, true)
                    }
                    if (activeFilter != "All") { list = list.filter { it.country.contains(activeFilter, true) } }
                    when (sortOrder) {
                        "Price" -> list.sortedByDescending { it.numericPrice }
                        "Rating" -> list.sortedByDescending { it.rating }
                        else -> list.sortedBy { it.name }
                    }
                }

                val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
                    if (success && currentPhotoPath != null) {
                        val file = File(currentPhotoPath!!)
                        isProcessing = true
                        scope.launch {
                            try {
                                val aiResult = aiHelper.analyzeWhiskey(file)
                                NetworkHelper.uploadToImgur(file) { url ->
                                    val finalDraft = aiResult?.copy(imageUrl = url) ?: Whiskey(imageUrl = url)
                                    capturedUrl = url
                                    aiDraftWhiskey = finalDraft
                                    isProcessing = false
                                    showAddDialog = true
                                }
                            } catch (e: Exception) {
                                isProcessing = false
                            }
                        }
                    }
                }

                val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                    if (isGranted) {
                        val file = File(context.cacheDir, "camera_photo_${System.currentTimeMillis()}.jpg")
                        currentPhotoPath = file.absolutePath
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        cameraLauncher.launch(uri)
                    }
                }

                // --- UI LAYOUT ---
                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    AsyncImage(
                        model = "https://images.unsplash.com/photo-1508253730651-e5ace80a7025?q=80&w=1470&auto=format&fit=crop",
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().alpha(0.2f),
                        contentScale = ContentScale.Crop
                    )

                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = Color.Transparent,
                        topBar = {
                            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                Text("WHISKEY VAULT", color = Color(0xFFFFBF00), fontSize = 24.sp, fontWeight = FontWeight.Black)
                                IconButton(onClick = { showSettingsDialog = true }) { Icon(Icons.Default.Settings, null, tint = Color.White) }
                            }
                        },
                        floatingActionButton = {
                            FloatingActionButton(onClick = { showSelectionDialog = true }, containerColor = Color(0xFFFFBF00)) { Icon(Icons.Default.Add, null) }
                        }
                    ) { innerPadding ->
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding), contentPadding = PaddingValues(bottom = 80.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            item { PremiumStatsCard(myCollection) }
                            item {
                                TextField(
                                    value = searchQuery, onValueChange = { searchQuery = it },
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                    placeholder = { Text("Search vault...", color = Color.Gray) },
                                    leadingIcon = { Icon(Icons.Default.Search, null, tint = Color(0xFFFFBF00)) },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = TextFieldDefaults.colors(focusedContainerColor = Color.White.copy(0.1f), unfocusedContainerColor = Color.White.copy(0.1f), focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                                )
                            }
                            item { SortingAndFilterRow(activeFilter, { activeFilter = it }, sortOrder, { sortOrder = it }) }
                            items(processedCollection) { w ->
                                Box(Modifier.padding(horizontal = 16.dp)) {
                                    PremiumWhiskeyCard(w, onClick = { selectedWhiskey = w })
                                }
                            }
                        }
                    }

                    // --- DIALOGER ---
                    if (showSelectionDialog) {
                        AlertDialog(
                            onDismissRequest = { showSelectionDialog = false },
                            title = { Text("Add Whiskey", color = Color(0xFFFFBF00)) },
                            text = { Text("Use AI to scan label?", color = Color.White) },
                            confirmButton = { Button(onClick = { showSelectionDialog = false; permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Scan") } },
                            dismissButton = { TextButton(onClick = { showSelectionDialog = false; showAddDialog = true }) { Text("Manual") } },
                            containerColor = Color(0xFF1E1E1E)
                        )
                    }

                    if (isProcessing) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(0.85f)), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Color(0xFFFFBF00))
                                Text("Gemini is analyzing...", color = Color.White, modifier = Modifier.padding(top = 16.dp))
                            }
                        }
                    }

                    if (selectedWhiskey != null) {
                        DetailDialog(
                            w = selectedWhiskey!!,
                            onDismiss = { selectedWhiskey = null },
                            onEdit = {
                                editingWhiskey = selectedWhiskey
                                capturedUrl = selectedWhiskey?.imageUrl
                                showAddDialog = true
                                selectedWhiskey = null
                            },
                            onDelete = { whiskeyToDelete ->
                                scope.launch { whiskeyDao.deleteWhiskey(whiskeyToDelete) }
                                selectedWhiskey = null
                            },
                            onRatingUpdate = { whiskeyToUpdate, newRating ->
                                scope.launch {
                                    val updated = whiskeyToUpdate.copy(rating = newRating)
                                    whiskeyDao.insertWhiskey(updated)
                                    selectedWhiskey = updated
                                }
                            }
                        )
                    }

                    if (showAddDialog) {
                        FullAddDialog(
                            existing = aiDraftWhiskey ?: editingWhiskey,
                            img = capturedUrl,
                            onDismiss = { showAddDialog = false; aiDraftWhiskey = null; editingWhiskey = null },
                            onSave = { scope.launch { whiskeyDao.insertWhiskey(it) }; showAddDialog = false; aiDraftWhiskey = null; editingWhiskey = null }
                        )
                    }
                }
            }
        }
    }
}

// --- KOMPONENTER ---

@Composable
fun PremiumStatsCard(list: List<Whiskey>) {
    Card(Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White.copy(0.08f)), shape = RoundedCornerShape(28.dp)) {
        Row(Modifier.padding(24.dp).fillMaxWidth(), Arrangement.SpaceEvenly) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("BOTTLES", color = Color(0xFFFFBF00), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("${list.size}", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val totalValue = list.sumOf { it.numericPrice }
                val formatted = NumberFormat.getNumberInstance(Locale("sv", "SE")).format(totalValue)
                Text("TOTAL VALUE", color = Color(0xFFFFBF00), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("$formatted:-", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
fun RatingBar(rating: Int, onRatingChanged: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier) {
        repeat(5) { index ->
            val starIndex = index + 1
            Icon(
                imageVector = if (starIndex <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = null,
                tint = if (starIndex <= rating) Color(0xFFFFBF00) else Color.White.copy(0.3f),
                modifier = Modifier.size(32.dp).clickable { onRatingChanged(starIndex) }
            )
        }
    }
}

@Composable
fun DetailDialog(w: Whiskey, onDismiss: () -> Unit, onEdit: () -> Unit, onDelete: (Whiskey) -> Unit, onRatingUpdate: (Whiskey, Int) -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Box(Modifier.fillMaxWidth().height(300.dp)) {
                    AsyncImage(model = w.imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    IconButton(onClick = { onDelete(w) }, Modifier.align(Alignment.TopEnd).padding(16.dp).background(Color.Black.copy(0.5f), CircleShape)) { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                    IconButton(onClick = onDismiss, Modifier.align(Alignment.TopStart).padding(16.dp).background(Color.Black.copy(0.5f), CircleShape)) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
                }
                Column(Modifier.padding(24.dp)) {
                    Text(w.name, color = Color(0xFFFFBF00), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    RatingBar(rating = w.rating, onRatingChanged = { onRatingUpdate(w, it) }, modifier = Modifier.padding(vertical = 12.dp))
                    Text("${w.country} • ${w.type}", color = Color.White.copy(0.7f))
                    Spacer(Modifier.height(16.dp))
                    Text("FLAVOR PROFILE", color = Color(0xFFFFBF00), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(w.flavorProfile.ifBlank { "No notes added." }, color = Color.White)
                    Spacer(Modifier.height(16.dp))
                    Text("PERSONAL NOTES", color = Color(0xFFFFBF00), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(w.notes.ifBlank { "No personal notes." }, color = Color.White)
                    Button(onClick = onEdit, modifier = Modifier.fillMaxWidth().padding(top = 32.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFBF00))) { Text("EDIT BOTTLE", color = Color.Black) }
                }
            }
        }
    }
}

@Composable
fun FullAddDialog(existing: Whiskey?, img: String?, onDismiss: () -> Unit, onSave: (Whiskey) -> Unit) {
    var name by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var abv by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("") }
    var volume by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val selectedFlavors = remember { mutableStateListOf<String>() }
    var openDropdown by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(existing) {
        existing?.let {
            name = it.name; country = it.country; price = it.price; abv = it.abv; type = it.type; volume = it.volume; notes = it.notes
            selectedFlavors.clear()
            if (it.flavorProfile.isNotBlank()) selectedFlavors.addAll(it.flavorProfile.split(",").map { it.trim() })
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF121212)) {
            Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState()).statusBarsPadding()) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("VAULT ENTRY", color = Color(0xFFFFBF00), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = Color.White) }
                }
                AsyncImage(model = img, contentDescription = null, modifier = Modifier.fillMaxWidth().height(180.dp).padding(vertical = 16.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), textStyle = TextStyle(color = Color.White))
                OutlinedTextField(value = country, onValueChange = { country = it }, label = { Text("Country") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), textStyle = TextStyle(color = Color.White))

                Row(Modifier.fillMaxWidth().padding(top = 8.dp), Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(value = type, onValueChange = { type = it }, label = { Text("Type") }, modifier = Modifier.weight(1f), textStyle = TextStyle(color = Color.White))
                    OutlinedTextField(value = volume, onValueChange = { volume = it }, label = { Text("Volume") }, modifier = Modifier.weight(1f), textStyle = TextStyle(color = Color.White))
                }

                Text("Flavors", color = Color(0xFFFFBF00), modifier = Modifier.padding(top = 24.dp), fontWeight = FontWeight.Bold)
                FLAVOR_MAP.forEach { (cat, list) ->
                    Box(Modifier.padding(vertical = 4.dp)) {
                        OutlinedButton(onClick = { openDropdown = if(openDropdown == cat) null else cat }, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text(cat, color = Color.White); Icon(Icons.Default.ArrowDropDown, null, tint = Color(0xFFFFBF00)) }
                        }
                        DropdownMenu(expanded = openDropdown == cat, onDismissRequest = { openDropdown = null }, modifier = Modifier.background(Color(0xFF2D2D2D))) {
                            list.forEach { n ->
                                DropdownMenuItem(
                                    text = { Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(selectedFlavors.contains(n), null); Text(n, color = Color.White) } },
                                    onClick = { if(selectedFlavors.contains(n)) selectedFlavors.remove(n) else selectedFlavors.add(n) }
                                )
                            }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth().padding(top = 16.dp), Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Price (SEK)") }, modifier = Modifier.weight(1f), textStyle = TextStyle(color = Color.White))
                    OutlinedTextField(value = abv, onValueChange = { abv = it }, label = { Text("ABV %") }, modifier = Modifier.weight(1f), textStyle = TextStyle(color = Color.White))
                }

                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(100.dp), textStyle = TextStyle(color = Color.White))
                Button(onClick = { onSave(Whiskey(id = existing?.id ?: 0, name = name, country = country, price = price, abv = abv, type = type, volume = volume, flavorProfile = selectedFlavors.joinToString(","), rating = existing?.rating ?: 5, imageUrl = img, notes = notes)) }, modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 40.dp).height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFBF00))) {
                    Text("SAVE TO VAULT", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun PremiumWhiskeyCard(w: Whiskey, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color.White.copy(0.05f)), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = w.imageUrl ?: "https://via.placeholder.com/150", contentDescription = null, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                Text(w.name, color = Color(0xFFFFBF00), fontWeight = FontWeight.Bold)
                Text("${w.type} • ${w.abv}%", color = Color.White.copy(0.6f), fontSize = 12.sp)
            }
            if(w.rating > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${w.rating}", color = Color(0xFFFFBF00), fontWeight = FontWeight.Bold)
                    Icon(Icons.Default.Star, null, tint = Color(0xFFFFBF00), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun SortingAndFilterRow(currentFilter: String, onFilterChange: (String) -> Unit, currentSort: String, onSortChange: (String) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Scotland", "USA", "Ireland", "Japan").forEach { country ->
                FilterChip(selected = currentFilter == country, onClick = { onFilterChange(country) }, label = { Text(country) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFFFBF00).copy(0.2f), selectedLabelColor = Color(0xFFFFBF00), labelColor = Color.White))
            }
        }
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Sort by:", color = Color.Gray, fontSize = 12.sp)
            listOf("Name", "Price", "Rating").forEach { sort ->
                Text(sort, color = if(currentSort == sort) Color(0xFFFFBF00) else Color.White, fontSize = 12.sp, modifier = Modifier.clickable { onSortChange(sort) })
            }
        }
    }
}