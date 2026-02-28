package com.schill.whiskeyvault.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.schill.whiskeyvault.Whiskey

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VaultDonutChart(
    title: String,
    data: Map<String, Int>,
    modifier: Modifier = Modifier
) {
    val total = data.values.sum().toFloat()
    if (total == 0f) return // Undviker krasch om valvet är tomt

    val colors = listOf(
        Color(0xFFFFBF00),
        Color(0xFFD4AF37),
        Color(0xFFCD7F32),
        Color(0xFFC0C0C0).copy(0.6f),
        Color(0xFFE5AA70)
    )

    Column(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title.uppercase(),
            color = Color(0xFFFFBF00),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
            Canvas(modifier = Modifier.size(180.dp)) {
                var startAngle = -90f
                data.values.forEachIndexed { index, value ->
                    val sweepAngle = (value / total) * 360f
                    drawArc(
                        color = colors[index % colors.size],
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        style = Stroke(width = 40f, cap = StrokeCap.Round),
                        size = Size(size.width, size.height)
                    )
                    startAngle += sweepAngle
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${total.toInt()}", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                Text("BOTTLES", color = Color.White.copy(0.5f), fontSize = 10.sp)
            }
        }

        FlowRow(
            modifier = Modifier.padding(top = 24.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            data.keys.forEachIndexed { index, label ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Box(Modifier.size(10.dp).padding(end = 4.dp)) {
                        Canvas(Modifier.fillMaxSize()) { drawCircle(colors[index % colors.size]) }
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(label, color = Color.White.copy(0.7f), fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun InsightsDialog(
    myCollection: List<Whiskey>,
    onDismiss: () -> Unit
) {
    val allFlavors = myCollection
        .flatMap { it.flavorProfile.split(",", " ", ".", "\n") }
        .map { it.trim().lowercase() }
        .filter { it.length > 3 }
        .filter { it !in listOf("whiskey", "smak", "noter", "flaska", "lite") }
        .groupingBy { it }
        .eachCount()
        .toList()
        .sortedByDescending { it.second }
        .take(5)

    val countryData = myCollection.groupBy { it.country }.mapValues { it.value.size }
    val typeData = myCollection.groupBy { it.type }.mapValues { it.value.size }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {

                Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("VAULT INSIGHTS", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = Color.White) }
                }

                if (allFlavors.isNotEmpty()) {
                    Text("DIN SMAKPROFIL", color = Color(0xFFFFBF00), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    allFlavors.forEach { (flavor, count) ->
                        FlavorBar(flavor = flavor, count = count, maxCount = allFlavors.first().second)
                    }
                    HorizontalDivider(Modifier.padding(vertical = 24.dp), color = Color.White.copy(0.1f))
                }

                if (countryData.isNotEmpty()) {
                    VaultDonutChart(title = "Distribution by Country", data = countryData)
                    HorizontalDivider(Modifier.padding(vertical = 24.dp), color = Color.White.copy(0.1f))
                }

                if (typeData.isNotEmpty()) {
                    VaultDonutChart(title = "Whiskey Types", data = typeData)
                }

                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
fun FlavorBar(flavor: String, count: Int, maxCount: Int) {
    val progress = if (maxCount > 0) count.toFloat() / maxCount.toFloat() else 0f

    Column(Modifier.padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text(flavor.uppercase(), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text("$count flaskor", color = Color.White.copy(0.5f), fontSize = 12.sp)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).background(Color.White.copy(0.1f), RoundedCornerShape(4.dp))) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .background(Color(0xFFFFBF00), RoundedCornerShape(4.dp))
            )
        }
    }
}