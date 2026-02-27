@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
package com.schill.whiskeyvault

import android.annotation.SuppressLint
import android.util.Log
import android.Manifest
import android.content.*
import android.location.Geocoder
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.Build
import android.media.ToneGenerator
import android.media.AudioManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.android.gms.location.LocationServices
import com.schill.whiskeyvault.ui.theme.WhiskeyVaultTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.NumberFormat
import java.util.*
import org.json.JSONObject

// --- MASTER DATA ---
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
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)

    private fun searchWhiskeyPrice(
        barcode: String,
        country: String,
        myCollection: List<Whiskey>,
        onResult: (Whiskey, Boolean) -> Unit,
        onError: () -> Unit
    ) {
        val generativeModel = GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = BuildConfig.GEMINI_KEY, // FIX 1: Ändrat från Secrets.GEMINI_KEY
            safetySettings = listOf(
                SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.NONE),
                SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.NONE),
                SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.NONE),
                SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.NONE)
            )
        )

        lifecycleScope.launch(Dispatchers.IO) {
            val prompt = """
                Identify whiskey from barcode $barcode. User in $country.
                Return ONLY JSON: {
                    "name":"full name",
                    "price":"estimated price in local currency",
                    "distillery":"..",
                    "type":"..",
                    "country":"..",
                    "stores":"List 2-3 popular retailers in $country that sell this as a plain string"
                }
            """.trimIndent()

            try {
                val response = generativeModel.generateContent(prompt)
                val cleanJson = response.text?.replace("```json", "")?.replace("```", "")?.trim() ?: "{}"
                val json = JSONObject(cleanJson)
                val scannedName = json.optString("name", "Unknown Whiskey")
                val isDuplicate = myCollection.any { it.name.equals(scannedName, ignoreCase = true) }

                val storeInfo = json.optString("stores", "N/A")
                    .replace("[", "").replace("]", "")
                    .replace("\"", "").trim()

                withContext(Dispatchers.Main) {
                    onResult(Whiskey(
                        name = scannedName,
                        price = json.optString("price", "N/A"),
                        country = json.optString("country", country),
                        type = json.optString("type", "Whiskey"),
                        notes = "Suggested stores: $storeInfo\nBarcode: $barcode"
                    ), isDuplicate)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError() }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        toneGenerator.release()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val database = WhiskeyDatabase.getDatabase(this)
        val whiskeyDao = database.whiskeyDao()

        setContent {
            WhiskeyVaultTheme {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                val vibrator = context.getSystemService(VIBRATOR_SERVICE) as Vibrator

                val aiHelper = remember { AiHelper(BuildConfig.GEMINI_KEY) } // FIX 2: Ändrat från Secrets.GEMINI_KEY
                val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

                val myCollection by whiskeyDao.getAllWhiskeys().collectAsState(initial = emptyList())
                var searchQuery by remember { mutableStateOf("") }
                var activeFilter by remember { mutableStateOf("All") }
                var sortOrder by remember { mutableStateOf("Name") }

                var showAddDialog by remember { mutableStateOf(false) }
                var showSelectionDialog by remember { mutableStateOf(false) }
                var showScanner by remember { mutableStateOf(false) }
                var isCodeDetected by remember { mutableStateOf(false) }
                var isDuplicateFound by remember { mutableStateOf(false) }
                var userCountry by remember { mutableStateOf("SE") }
                var selectedWhiskey by remember { mutableStateOf<Whiskey?>(null) }
                var editingWhiskey by remember { mutableStateOf<Whiskey?>(null) }
                var aiDraftWhiskey by remember { mutableStateOf<Whiskey?>(null) }
                var isProcessing by remember { mutableStateOf(false) }
                var capturedUrl by remember { mutableStateOf<String?>(null) }
                var scannedResult by remember { mutableStateOf<Whiskey?>(null) }

                val detectionHistory = remember { mutableStateListOf<String>() }
                var lastDetectionTime by remember { mutableLongStateOf(0L) }

                @SuppressLint("MissingPermission")
                fun updateLocation() {
                    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                        location?.let {
                            val geocoder = Geocoder(context, Locale.getDefault())
                            val addresses = geocoder.getFromLocation(it.latitude, it.longitude, 1)
                            userCountry = addresses?.get(0)?.countryCode ?: "SE"
                        }
                    }
                }

                val processedCollection = remember(myCollection, searchQuery, activeFilter, sortOrder) {
                    var list = myCollection.filter {
                        (it.name.contains(searchQuery, true) || it.flavorProfile.contains(searchQuery, true))
                    }

                    list = when (activeFilter) {
                        "All" -> list.filter { !it.isWishlist }
                        "Wishlist" -> list.filter { it.isWishlist }
                        else -> list.filter { it.country.contains(activeFilter, true) && !it.isWishlist }
                    }

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
                                val result = aiHelper.analyzeWhiskey(file)
                                NetworkHelper.uploadToImgur(file) { url ->
                                    capturedUrl = url
                                    aiDraftWhiskey = result
                                    isProcessing = false
                                    showAddDialog = true
                                }
                            } catch (e: Exception) { isProcessing = false }
                        }
                    }
                }

                val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
                    if (perms[Manifest.permission.CAMERA] == true) {
                        val file = File(context.cacheDir, "photo_${System.currentTimeMillis()}.jpg")
                        currentPhotoPath = file.absolutePath
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        cameraLauncher.launch(uri)
                    }
                }

                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    AsyncImage(
                        model = "https://images.unsplash.com/photo-1508253730651-e5ace80a7025?q=80&w=1470&auto=format&fit=crop",
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().alpha(0.25f),
                        contentScale = ContentScale.Crop
                    )

                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = Color.Transparent,
                        topBar = {
                            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                Text("WHISKEY VAULT", color = Color(0xFFFFBF00), fontSize = 24.sp, fontWeight = FontWeight.Black)

                                Row {
                                    // --- NY DELA-FUNKTION FÖR WISHLIST ---
                                    IconButton(onClick = {
                                        // Filtrera ut alla flaskor som är på wishlist
                                        val wishlist = myCollection.filter { it.isWishlist }

                                        // Skapa en snygg text
                                        val shareText = if (wishlist.isNotEmpty()) {
                                            "🥃 Min Whiskey Wishlist:\n\n" + wishlist.joinToString("\n") { bottle ->
                                                "- ${bottle.name} (${bottle.type})"
                                            }
                                        } else {
                                            "Min Wishlist är tom just nu! Dags att jaga ny whiskey. 🥃"
                                        }

                                        // Starta Androids inbyggda dela-meny
                                        val sendIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_TEXT, shareText)
                                            type = "text/plain"
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, "Dela Wishlist"))
                                    }) {
                                        Icon(Icons.Default.Share, contentDescription = "Dela", tint = Color.White)
                                    }

                                    // Befintlig inställningsknapp
                                    IconButton(onClick = { }) { Icon(Icons.Default.Settings, null, tint = Color.White) }
                                }
                            }
                        },
                        floatingActionButton = {
                            FloatingActionButton(onClick = { showSelectionDialog = true }, containerColor = Color(0xFFFFBF00)) { Icon(Icons.Default.Add, null) }
                        }
                    ) { innerPadding ->
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding), contentPadding = PaddingValues(bottom = 80.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            item { PremiumStatsCard(myCollection.filter { !it.isWishlist }) }
                            item {
                                TextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                    placeholder = { Text("Search vault...", color = Color.Gray) },
                                    leadingIcon = { Icon(Icons.Default.Search, null, tint = Color(0xFFFFBF00)) },
                                   // trailingIcon = {
                                     //   IconButton(onClick = { updateLocation(); showScanner = true; permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_COARSE_LOCATION)) }) {
                                       //     Icon(Icons.Default.QrCodeScanner, null, tint = Color(0xFFFFBF00))
                                        //}
                                    //},
                                    shape = RoundedCornerShape(16.dp),
                                    colors = TextFieldDefaults.colors(focusedContainerColor = Color.White.copy(0.1f), unfocusedContainerColor = Color.White.copy(0.1f), focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, focusedTextColor = Color.White)
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

                    if (showScanner) {
                        LiveScannerDialog(
                            isDetected = isCodeDetected,
                            onDismiss = { showScanner = false; isCodeDetected = false; detectionHistory.clear() },
                            onCodeScanned = { barcode ->
                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastDetectionTime > 150) {
                                    lastDetectionTime = currentTime
                                    detectionHistory.add(barcode)
                                    if (detectionHistory.size > 6) detectionHistory.removeAt(0)
                                    val occurrences = detectionHistory.count { it == barcode }
                                    if (occurrences >= 5 && !isCodeDetected) {
                                        isCodeDetected = true
                                        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                                        vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                                        lifecycleScope.launch {
                                            delay(1000)
                                            showScanner = false
                                            isCodeDetected = false
                                            detectionHistory.clear()
                                            isProcessing = true
                                            searchWhiskeyPrice(barcode, userCountry, myCollection, { result, isDuplicate ->
                                                scannedResult = result
                                                isDuplicateFound = isDuplicate
                                                isProcessing = false
                                            }, { isProcessing = false })
                                        }
                                    }
                                }
                            }
                        )
                    }

                    if (isProcessing) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(0.85f)), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Color(0xFFFFBF00))
                                Text("AI is hunting for details & stores...", color = Color.White, modifier = Modifier.padding(top = 16.dp))
                            }
                        }
                    }

                    if (showSelectionDialog) {
                        AlertDialog(
                            onDismissRequest = { showSelectionDialog = false },
                            title = { Text("Add Whiskey", color = Color(0xFFFFBF00)) },
                            text = { Text("Scan label or enter manually?", color = Color.White) },
                            confirmButton = { Button(onClick = { showSelectionDialog = false; permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA)) }) { Text("Scan Label") } },
                            dismissButton = { TextButton(onClick = { showSelectionDialog = false; showAddDialog = true }) { Text("Manual") } },
                            containerColor = Color(0xFF1E1E1E)
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

                    if (selectedWhiskey != null) {
                        DetailDialog(
                            w = selectedWhiskey!!,
                            onDismiss = { selectedWhiskey = null },
                            onEdit = { editingWhiskey = selectedWhiskey; capturedUrl = selectedWhiskey?.imageUrl; showAddDialog = true; selectedWhiskey = null },
                            onDelete = { scope.launch { whiskeyDao.deleteWhiskey(it) }; selectedWhiskey = null },
                            onRatingUpdate = { w, r -> scope.launch { whiskeyDao.insertWhiskey(w.copy(rating = r)); selectedWhiskey = w.copy(rating = r) } }
                        )
                    }
                }
            }
        }
    }
}

// --- UI COMPONENTS ---

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FullAddDialog(existing: Whiskey?, img: String?, onDismiss: () -> Unit, onSave: (Whiskey) -> Unit) {
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
            status = it.status
            isWishlist = it.isWishlist
            if (it.notes.startsWith("Suggested stores:")) {
                val split = it.notes.split("\n\n")
                aiStores = split[0].replace("[", "").replace("]", "").replace("\"", "")
                notes = if (split.size > 1) split[1] else ""
            } else {
                notes = it.notes; aiStores = ""
            }
            selectedFlavors.clear()
            if (it.flavorProfile.isNotBlank()) selectedFlavors.addAll(it.flavorProfile.split(",").map { it.trim() })
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF121212)) {
            Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState()).statusBarsPadding()) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("VAULT ENTRY", color = Color(0xFFFFBF00), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = Color.White) }
                }

                AsyncImage(model = img, contentDescription = null, modifier = Modifier.fillMaxWidth().height(180.dp).padding(vertical = 16.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                    Checkbox(checked = isWishlist, onCheckedChange = { isWishlist = it }, colors = CheckboxDefaults.colors(checkedColor = Color(0xFFFFBF00)))
                    Text("Add to Wishlist", color = Color.White)
                }

                if (!isWishlist) {
                    Text("Bottle Status", color = Color(0xFFFFBF00), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), Arrangement.spacedBy(8.dp)) {
                        listOf("Unopened", "Open", "Empty").forEach { s ->
                            FilterChip(
                                selected = status == s,
                                onClick = { status = s },
                                label = { Text(s) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFFFBF00).copy(0.2f), selectedLabelColor = Color(0xFFFFBF00), labelColor = Color.White)
                            )
                        }
                    }
                }

                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), textStyle = TextStyle(color = Color.White))

                if (aiStores.isNotBlank()) {
                    Card(Modifier.fillMaxWidth().padding(top = 16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFBF00).copy(0.1f)), border = BorderStroke(1.dp, Color(0xFFFFBF00).copy(0.5f))) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Store, null, tint = Color(0xFFFFBF00))
                            Spacer(Modifier.width(8.dp))
                            Text(aiStores, color = Color.White, fontSize = 12.sp)
                        }
                    }
                }

                OutlinedTextField(value = country, onValueChange = { country = it }, label = { Text("Country") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), textStyle = TextStyle(color = Color.White))

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

                FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    selectedFlavors.forEach { SuggestionChip(onClick = { }, label = { Text(it, fontSize = 10.sp) }) }
                }

                Row(Modifier.fillMaxWidth().padding(top = 16.dp), Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Price") }, modifier = Modifier.weight(1f), textStyle = TextStyle(color = Color.White))
                    OutlinedTextField(value = abv, onValueChange = { abv = it }, label = { Text("ABV %") }, modifier = Modifier.weight(1f), textStyle = TextStyle(color = Color.White))
                }

                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("MY NOTES") }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp), textStyle = TextStyle(color = Color.White), minLines = 3)

                Button(onClick = {
                    val finalNotes = if (aiStores.isNotBlank()) "$aiStores\n\n$notes" else notes
                    onSave(Whiskey(id = existing?.id ?: 0, name = name, country = country, price = price, abv = abv, type = type, volume = volume, flavorProfile = selectedFlavors.joinToString(","), rating = existing?.rating ?: 5, imageUrl = img, notes = finalNotes.trim(), status = status, isWishlist = isWishlist))
                }, modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 85.dp).height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFBF00))) {
                    Text("SAVE TO VAULT", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

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
                if (w.isWishlist) {
                    Icon(Icons.Default.Favorite, null, tint = Color.Red, modifier = Modifier.size(20.dp).align(Alignment.TopEnd).padding(2.dp))
                }
            }

            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                Text(w.name, color = Color(0xFFFFBF00), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("${w.type} • ${w.abv}%", color = Color.White.copy(0.6f), fontSize = 12.sp)

                if (!w.isWishlist) {
                    Spacer(Modifier.height(8.dp))
                    val statusColor = when(w.status) {
                        "Unopened" -> Color(0xFF4CAF50)
                        "Open" -> Color(0xFFFFBF00)
                        else -> Color.Red
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(statusColor))
                        Text("  ${w.status}", color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                } else {
                    Spacer(Modifier.height(8.dp))
                    Text("Wishlist", color = Color.Red.copy(0.8f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun PremiumStatsCard(list: List<Whiskey>) {
    val totalBottles = list.size
    val unopenedCount = list.count { it.status == "Unopened" }
    val openCount = list.count { it.status == "Open" }
    val emptyCount = list.count { it.status == "Empty" }

    val totalValue = list.sumOf { it.numericPrice }
    val formattedValue = NumberFormat.getNumberInstance(Locale("sv", "SE")).format(totalValue)

    Card(
        Modifier.fillMaxWidth().padding(16.dp),
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
            Spacer(Modifier.height(20.dp))
            Divider(color = Color.White.copy(0.1f), thickness = 1.dp)
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceAround) {
                StatusMiniStat("UNOPENED", unopenedCount, Color(0xFF4CAF50))
                StatusMiniStat("OPEN", openCount, Color(0xFFFFBF00))
                StatusMiniStat("EMPTY", emptyCount, Color.Red)
            }
        }
    }
}

@Composable
fun StatusMiniStat(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = color.copy(alpha = 0.8f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(color))
            Text(" $count", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SortingAndFilterRow(currentFilter: String, onFilterChange: (String) -> Unit, currentSort: String, onSortChange: (String) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Wishlist", "Scotland", "USA", "Ireland", "Japan").forEach { country ->
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
                    if (w.isWishlist) {
                        Text("❤️ ON WISHLIST", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    } else {
                        Text("Status: ${w.status}", color = Color.White.copy(0.7f))
                    }
                    Row(Modifier.padding(vertical = 12.dp)) {
                        repeat(5) { i -> Icon(if (i < w.rating) Icons.Default.Star else Icons.Default.StarBorder, null, tint = Color(0xFFFFBF00), modifier = Modifier.clickable { onRatingUpdate(w, i + 1) }) }
                    }
                    Text("${w.country} • ${w.type}", color = Color.White.copy(0.7f))
                    Spacer(Modifier.height(16.dp))
                    Text(w.flavorProfile, color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text(w.notes, color = Color.White.copy(0.6f), fontSize = 14.sp)
                    Button(onClick = onEdit, modifier = Modifier.fillMaxWidth().padding(top = 32.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFBF00))) { Text("EDIT BOTTLE", color = Color.Black) }
                }
            }
        }
    }
}

@Composable
fun LiveScannerDialog(isDetected: Boolean, onDismiss: () -> Unit, onCodeScanned: (String) -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            if (!isDetected) {
                BarcodeScannerView(modifier = Modifier.fillMaxSize()) { barcodeValue: String -> onCodeScanned(barcodeValue) }
            }
            ScannerOverlay(isDetected = isDetected, onDismiss = onDismiss)
        }
    }
}

@Composable
fun ScannerOverlay(isDetected: Boolean, onDismiss: () -> Unit) {
    val borderColor by animateColorAsState(targetValue = if (isDetected) Color(0xFF4CAF50) else Color(0xFFFFBF00), label = "")
    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val scannerSize = size.width * 0.7f
            val left = (size.width - scannerSize) / 2
            val top = (size.height - scannerSize) / 2
            drawRect(Color.Black.copy(alpha = if (isDetected) 0.4f else 0.7f))
            drawRoundRect(color = Color.Transparent, topLeft = Offset(left, top), size = Size(scannerSize, scannerSize), blendMode = BlendMode.Clear, cornerRadius = CornerRadius(16.dp.toPx()))
            drawRoundRect(color = borderColor, topLeft = Offset(left, top), size = Size(scannerSize, scannerSize), style = Stroke(width = if (isDetected) 6.dp.toPx() else 3.dp.toPx()), cornerRadius = CornerRadius(16.dp.toPx()))
        }
        IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopStart).padding(top = 40.dp, start = 20.dp)) { Icon(Icons.Default.Close, null, tint = Color.White) }
        Text(text = if (isDetected) "MATCH!" else "Align barcode", color = if (isDetected) Color(0xFF4CAF50) else Color.White, modifier = Modifier.align(Alignment.Center).padding(top = 380.dp))
    }
}