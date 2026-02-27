package com.schill.whiskeyvault.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.*
import com.schill.whiskeyvault.Whiskey

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

    // --- ZOOMAD BILD ---
    if (showFullScreenImage) {
        Dialog(
            onDismissRequest = { showFullScreenImage = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black).clickable { showFullScreenImage = false }) {
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
                    // --- TOPPBILD ---
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

                        // --- RATING ---
                        Row(Modifier.padding(vertical = 12.dp)) {
                            repeat(5) { i ->
                                Icon(
                                    if (i < w.rating) Icons.Default.Star else Icons.Default.StarBorder,
                                    null,
                                    tint = Color(0xFFFFBF00),
                                    modifier = Modifier.clickable { onRatingUpdate(w, i + 1) })
                            }
                        }

                        Text("${w.country} • ${w.type}", color = Color.White.copy(0.7f))

                        // --- LOGGA DRAM-KNAPP ---
                        if (w.status == "Open") {
                            Spacer(Modifier.height(24.dp))
                            Text("TASTING JOURNAL", color = Color(0xFFFFBF00), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            OutlinedButton(
                                onClick = { onEdit() }, // Vi skickar användaren till Edit för att skriva sin logg
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

                        // --- NOTERINGAR & LOGGAR ---
                        val noteSections = w.notes.split("\n\n")
                        noteSections.forEach { section ->
                            if (section.startsWith("[LOG")) {
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(0.05f))
                                ) {
                                    Text(text = section, modifier = Modifier.padding(12.dp), color = Color(0xFFFFBF00).copy(0.9f), fontSize = 13.sp)
                                }
                            } else {
                                Text(section, color = Color.White.copy(0.6f), fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
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