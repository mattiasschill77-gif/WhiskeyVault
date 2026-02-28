@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
    ExperimentalFoundationApi::class
)
package com.schill.whiskeyvault

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.google.android.gms.location.LocationServices
import com.schill.whiskeyvault.ui.*
import com.schill.whiskeyvault.ui.theme.WhiskeyVaultTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var currentPhotoPath: String? = null
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)

    // Denna funktion använder nu AiHelper för att söka på streckkod
    private fun searchWhiskeyPrice(
        barcode: String,
        country: String,
        myCollection: List<Whiskey>,
        onResult: (Whiskey, Boolean) -> Unit,
        onError: () -> Unit
    ) {
        val aiHelper = AiHelper(apiKey = BuildConfig.GEMINI_API_KEY)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Skapar en tillfällig fil för att låtsas vara en bild för AiHelper (eftersom vi bara har barcode här)
                // OBS: I en framtida version kanske du vill bygga en specifik text-prompt funktion i AiHelper för just streckkoder.
                // För nu hanterar vi det smidigt här inne:
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

                // En liten fulhack tills vi bygger ut AiHelper för streckkoder:
                // Vi låter AiHelper hantera en dummy-fil och hoppas den läser barcode.
                // Idealet är att lägga in en 'analyzeBarcode(barcode)' i AiHelper.
                withContext(Dispatchers.Main) {
                    // Fallback tills vidare: Returnerar en tom draft så användaren får skriva in
                    onResult(
                        Whiskey(
                            name = "Scanned Barcode: $barcode",
                            country = country,
                            price = "0",
                            type = "Unknown",
                            notes = "AI Barcode search needs update."
                        ), false
                    )
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

    private fun exportToCsv(context: Context, list: List<Whiskey>) {
        val csvHeader = "Namn;Land;Typ;Pris;Betyg;Status;Noteringar\n"
        val csvContent = list.joinToString("\n") { w ->
            val cleanNotes = w.notes.replace("\n", " ").replace(";", ",")
            "${w.name};${w.country};${w.type};${w.price};${w.rating};${w.status};$cleanNotes"
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, csvHeader + csvContent)
            type = "text/plain"
        }
        context.startActivity(Intent.createChooser(intent, "Exportera Vault"))
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

                val aiHelper = remember { AiHelper(BuildConfig.GEMINI_API_KEY) }
                val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

                val myCollection by whiskeyDao.getAllWhiskeys().collectAsState(initial = emptyList())
                var searchQuery by remember { mutableStateOf("") }
                var activeFilter by remember { mutableStateOf("All") }
                var sortOrder by remember { mutableStateOf("Name") }

                var showAddDialog by remember { mutableStateOf(false) }
                var showSelectionDialog by remember { mutableStateOf(false) }
                var showRetryDialog by remember { mutableStateOf(false) }
                var showScanner by remember { mutableStateOf(false) }
                var isCodeDetected by remember { mutableStateOf(false) }
                var isDuplicateFound by remember { mutableStateOf(false) }
                var userCountry by remember { mutableStateOf("SE") }
                var selectedWhiskey by remember { mutableStateOf<Whiskey?>(null) }
                var showStats by remember { mutableStateOf(false) }
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

                LaunchedEffect(Unit) { updateLocation() }

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
                                isProcessing = false

                                if (result == null) {
                                    // AI:n sa nej! Öppna skämtdialogen istället för Add-dialogen.
                                    showRetryDialog = true
                                } else {
                                    // Allt gick bra!
                                    aiDraftWhiskey = result
                                    showAddDialog = true
                                }
                            } catch (e: Exception) {
                                isProcessing = false
                            }
                        }
                    }
                }

                val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
                    if (perms[Manifest.permission.CAMERA] == true) {
                        val file = File(context.cacheDir, "photo_${System.currentTimeMillis()}.jpg")
                        currentPhotoPath = file.absolutePath
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
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
                            Row(
                                Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
                                Arrangement.SpaceBetween,
                                Alignment.CenterVertically
                            ) {
                                Text("WHISKEY VAULT", color = Color(0xFFFFBF00), fontSize = 24.sp, fontWeight = FontWeight.Black)

                                Row {
                                    IconButton(onClick = {
                                        val wishlist = myCollection.filter { it.isWishlist }
                                        val shareText = if (wishlist.isNotEmpty()) {
                                            "🥃 Min Whiskey Wishlist:\n\n" + wishlist.joinToString("\n") { "- ${it.name} (${it.type})" }
                                        } else {
                                            "Min Wishlist är tom just nu! 🥃"
                                        }

                                        val sendIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_TEXT, shareText)
                                            type = "text/plain"
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, "Dela Wishlist"))
                                    }) {
                                        Icon(Icons.Default.Share, null, tint = Color.White)
                                    }
                                    IconButton(onClick = { exportToCsv(context, myCollection) }) {
                                        Icon(Icons.Default.FileDownload, contentDescription = "Export CSV", tint = Color.White)
                                    }
                                }
                            }
                        },
                        floatingActionButton = {
                            FloatingActionButton(
                                onClick = { showSelectionDialog = true },
                                containerColor = Color(0xFFFFBF00)
                            ) { Icon(Icons.Default.Add, null) }
                        }
                    ) { innerPadding ->
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(innerPadding),
                            contentPadding = PaddingValues(bottom = 80.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item {
                                PremiumStatsCard(
                                    list = myCollection.filter { !it.isWishlist },
                                    onClick = { showStats = true }
                                )
                            }

                            item {
                                TextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                    placeholder = { Text("Search vault...", color = Color.Gray) },
                                    leadingIcon = { Icon(Icons.Default.Search, null, tint = Color(0xFFFFBF00)) },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.White.copy(0.1f),
                                        unfocusedContainerColor = Color.White.copy(0.1f),
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        focusedTextColor = Color.White
                                    )
                                )
                            }

                            item {
                                SortingAndFilterRow(activeFilter, { activeFilter = it }, sortOrder, { sortOrder = it })
                            }

                            if (processedCollection.isEmpty()) {
                                item { EmptyVaultState(searchQuery, activeFilter) }
                            } else {
                                items(processedCollection, key = { it.id }) { w ->
                                    Box(modifier = Modifier.padding(horizontal = 16.dp).animateItemPlacement()) {
                                        PremiumWhiskeyCard(w, onClick = { selectedWhiskey = w })
                                    }
                                }
                            }
                        }

                        if (showScanner) {
                            LiveScannerDialog(
                                isDetected = isCodeDetected,
                                onDismiss = {
                                    showScanner = false; isCodeDetected = false; detectionHistory.clear()
                                },
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
                                                searchWhiskeyPrice(
                                                    barcode,
                                                    userCountry,
                                                    myCollection,
                                                    { result, isDuplicate ->
                                                        scannedResult = result
                                                        isDuplicateFound = isDuplicate
                                                        isProcessing = false
                                                    },
                                                    { isProcessing = false }
                                                )
                                            }
                                        }
                                    }
                                }
                            )
                        }

                        if (isProcessing) {
                            Box(
                                Modifier.fillMaxSize().background(Color.Black.copy(0.85f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = Color(0xFFFFBF00))
                                    Text("AI is analyzing...", color = Color.White, modifier = Modifier.padding(top = 16.dp))
                                }
                            }
                        }

                        if (showSelectionDialog) {
                            AlertDialog(
                                onDismissRequest = { showSelectionDialog = false },
                                title = { Text("Add Whiskey", color = Color(0xFFFFBF00)) },
                                text = { Text("Scan label or enter manually?", color = Color.White) },
                                confirmButton = {
                                    Button(onClick = {
                                        showSelectionDialog = false; permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                                    }) { Text("Scan Label") }
                                },
                                dismissButton = {
                                    TextButton(onClick = {
                                        showSelectionDialog = false; showAddDialog = true
                                    }) { Text("Manual") }
                                },
                                containerColor = Color(0xFF1E1E1E)
                            )
                        }
                        if (showRetryDialog) {
                            AlertDialog(
                                onDismissRequest = { showRetryDialog = false },
                                title = { Text("Are you drunk? 😹", color = Color(0xFFFFBF00)) },
                                text = {
                                    Text(
                                        "That doesn't look like a whiskey bottle at all!\n\nDo you want to try scanning again or enter the details manually?",
                                        color = Color.White
                                    )
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            showRetryDialog = false
                                            // Startar om kameran direkt!
                                            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFBF00))
                                    ) { Text("Try Again", color = Color.Black) }
                                },
                                dismissButton = {
                                    TextButton(onClick = {
                                        showRetryDialog = false
                                        aiDraftWhiskey = null // Säkerställer att den är tom
                                        showAddDialog = true  // Öppnar manuella rutan
                                    }) { Text("Manual Entry", color = Color.White) }
                                },
                                containerColor = Color(0xFF1E1E1E)
                            )
                        }

                        // Dialogerna anropas här!
                        if (showStats) {
                            InsightsDialog(myCollection = myCollection, onDismiss = { showStats = false })
                        }

                        if (showAddDialog) {
                            // Antar att FullAddDialog finns i dina UI-filer
                            FullAddDialog(
                                existing = aiDraftWhiskey ?: editingWhiskey,
                                img = capturedUrl,
                                onDismiss = {
                                    showAddDialog = false; aiDraftWhiskey = null; editingWhiskey = null
                                },
                                onSave = {
                                    scope.launch { whiskeyDao.insertWhiskey(it) }; showAddDialog = false; aiDraftWhiskey = null; editingWhiskey = null
                                }
                            )
                        }

                        if (selectedWhiskey != null) {
                            DetailDialog(
                                w = selectedWhiskey!!,
                                onDismiss = { selectedWhiskey = null },
                                onEdit = {
                                    editingWhiskey = selectedWhiskey; capturedUrl = selectedWhiskey?.imageUrl; showAddDialog = true; selectedWhiskey = null
                                },
                                onDelete = {
                                    scope.launch { whiskeyDao.deleteWhiskey(it) }; selectedWhiskey = null
                                },
                                onRatingUpdate = { w, r ->
                                    scope.launch {
                                        whiskeyDao.insertWhiskey(w.copy(rating = r)); selectedWhiskey = w.copy(rating = r)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- TOP LEVEL UI COMPONENTS ---

@Composable
fun DetailDialog(
    w: Whiskey,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: (Whiskey) -> Unit,
    onRatingUpdate: (Whiskey, Int) -> Unit
) {
    var showFullScreenImage by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    if (showFullScreenImage) {
        Dialog(
            onDismissRequest = { showFullScreenImage = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                AsyncImage(
                    model = w.imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                IconButton(
                    onClick = { showFullScreenImage = false },
                    Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = 16.dp).background(Color.Black.copy(0.5f), CircleShape)
                ) { Icon(Icons.Default.Close, null, tint = Color.White) }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 10 }),
            exit = fadeOut()
        ) {
            Surface(Modifier.fillMaxSize(), color = Color.Black) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Box(Modifier.fillMaxWidth().height(300.dp).clickable { showFullScreenImage = true }) {
                        AsyncImage(
                            model = w.imageUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = { onDelete(w) },
                            Modifier.align(Alignment.TopEnd).padding(16.dp).background(Color.Black.copy(0.5f), CircleShape)
                        ) { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                        IconButton(
                            onClick = onDismiss,
                            Modifier.align(Alignment.TopStart).padding(16.dp).background(Color.Black.copy(0.5f), CircleShape)
                        ) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
                    }
                    Column(Modifier.padding(24.dp)) {
                        Text(w.name, color = Color(0xFFFFBF00), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        if (w.isWishlist) {
                            Text("❤️ ON WISHLIST", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        } else {
                            Text("Status: ${w.status}", color = Color.White.copy(0.7f))
                        }
                        Row(Modifier.padding(vertical = 12.dp)) {
                            repeat(5) { i ->
                                Icon(
                                    if (i < w.rating) Icons.Default.Star else Icons.Default.StarBorder,
                                    null,
                                    tint = Color(0xFFFFBF00),
                                    modifier = Modifier.clickable { onRatingUpdate(w, i + 1) }
                                )
                            }
                        }
                        Text("${w.country} • ${w.type}", color = Color.White.copy(0.7f))

                        if (w.status == "Open") {
                            Spacer(Modifier.height(24.dp))
                            Text("TASTING JOURNAL", color = Color(0xFFFFBF00), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            OutlinedButton(
                                onClick = { onEdit() },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                border = BorderStroke(1.dp, Color(0xFFFFBF00).copy(0.5f))
                            ) {
                                Icon(Icons.Default.HistoryEdu, null, tint = Color(0xFFFFBF00))
                                Spacer(Modifier.width(8.dp))
                                Text("LOG A NEW DRAM", color = Color.White)
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        Text("FLAVORS & NOTES", color = Color(0xFFFFBF00), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(w.flavorProfile, color = Color.White, modifier = Modifier.padding(vertical = 8.dp))

                        val noteSections = w.notes.split("\n\n")
                        noteSections.forEach { section ->
                            if (section.startsWith("[LOG")) {
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(0.05f))
                                ) {
                                    Text(
                                        text = section,
                                        modifier = Modifier.padding(12.dp),
                                        color = Color(0xFFFFBF00).copy(0.9f),
                                        fontSize = 13.sp
                                    )
                                }
                            } else {
                                Text(
                                    section,
                                    color = Color.White.copy(0.6f),
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }

                        Button(
                            onClick = onEdit,
                            modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFBF00))
                        ) { Text("EDIT BOTTLE", color = Color.Black) }
                    }
                }
            }
        }
    }
}

@Composable
fun LiveScannerDialog(
    isDetected: Boolean,
    onDismiss: () -> Unit,
    onCodeScanned: (String) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            if (!isDetected) {
                // Notera: BarcodeScannerView måste finnas bland dina UI-komponenter
                BarcodeScannerView(modifier = Modifier.fillMaxSize()) { onCodeScanned(it) }
            }
            ScannerOverlay(isDetected = isDetected, onDismiss = onDismiss)
        }
    }
}

@Composable
fun ScannerOverlay(isDetected: Boolean, onDismiss: () -> Unit) {
    val borderColor by animateColorAsState(
        targetValue = if (isDetected) Color(0xFF4CAF50) else Color(0xFFFFBF00), label = ""
    )
    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val scannerSize = size.width * 0.7f
            val left = (size.width - scannerSize) / 2
            val top = (size.height - scannerSize) / 2

            drawRect(Color.Black.copy(0.7f))

            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(left, top),
                size = Size(scannerSize, scannerSize),
                cornerRadius = CornerRadius(16.dp.toPx()),
                blendMode = BlendMode.Clear
            )

            drawRoundRect(
                color = borderColor,
                topLeft = Offset(left, top),
                size = Size(scannerSize, scannerSize),
                cornerRadius = CornerRadius(16.dp.toPx()),
                style = Stroke(3.dp.toPx())
            )
        }
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.TopStart).padding(top = 40.dp, start = 20.dp)
        ) {
            Icon(Icons.Default.Close, null, tint = Color.White)
        }
    }
}