package com.example.localjupyter.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.localjupyter.model.CellOutput
import com.example.ui.theme.*

@Composable
fun RichOutputView(
    outputs: List<CellOutput>,
    modifier: Modifier = Modifier
) {
    if (outputs.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF141416))
            .border(1.dp, Color(0xFF27272A), RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        outputs.forEachIndexed { index, output ->
            OutputItem(output = output, index = index)
        }
    }
}

@Composable
private fun OutputItem(output: CellOutput, index: Int) {
    var isExpanded by remember { mutableStateOf(true) }
    var showImageModal by remember { mutableStateOf<String?>(null) }

    when (output) {
        is CellOutput.Stream -> {
            val isStderr = output.name == "stderr"
            val text = output.text
            val lines = text.lines()
            val isLong = lines.size > 12

            Column {
                if (isLong) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isExpanded = !isExpanded }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isExpanded) "Collapse output" else "Expand output (${lines.size} lines)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Toggle output",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val displayText = if (isLong && !isExpanded) lines.take(8).joinToString("\n") + "\n..." else text
                Text(
                    text = parseAnsiEscapeCodes(displayText),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = if (isStderr) ErrorRose else OnSurfaceDark,
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .testTag("stream_output_$index")
                )
            }
        }

        is CellOutput.ExecuteResult -> {
            MimeBundleRenderer(
                data = output.data,
                executionCount = output.executionCount,
                onZoomImage = { showImageModal = it }
            )
        }

        is CellOutput.DisplayData -> {
            MimeBundleRenderer(
                data = output.data,
                executionCount = null,
                onZoomImage = { showImageModal = it }
            )
        }

        is CellOutput.Error -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF2A1519))
                    .border(1.dp, ErrorRose.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .padding(8.dp)
                    .testTag("error_output_$index")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = "Error",
                        tint = ErrorRose,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${output.ename}: ${output.evalue}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = ErrorRose
                        )
                    )
                }

                if (output.traceback.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    val fullTb = output.traceback.joinToString("\n")
                    Text(
                        text = parseAnsiEscapeCodes(fullTb),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = Color(0xFFFECDD3),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    )
                }
            }
        }
    }

    // Full screen image modal
    showImageModal?.let { base64Str ->
        Dialog(onDismissRequest = { showImageModal = null }) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SurfaceDark,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Plot Preview", style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { showImageModal = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Base64ImageView(base64Str = base64Str, isZoomed = true)
                }
            }
        }
    }
}

@Composable
private fun MimeBundleRenderer(
    data: Map<String, Any?>,
    executionCount: Int?,
    onZoomImage: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (executionCount != null) {
            Text(
                text = "Out[$executionCount]:",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = JupyterOrange,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        // Render Priority: 1. image/png, 2. text/html, 3. text/plain
        val imagePng = data["image/png"] as? String
        val textHtml = data["text/html"] as? String
        val textPlain = data["text/plain"]?.toString()

        if (!imagePng.isNullOrEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onZoomImage(imagePng) }
            ) {
                Base64ImageView(base64Str = imagePng, isZoomed = false)
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomIn,
                            contentDescription = "Zoom",
                            modifier = Modifier.size(14.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Tap to zoom", fontSize = 10.sp, color = Color.White)
                    }
                }
            }
        } else if (!textHtml.isNullOrEmpty()) {
            HtmlTableRenderer(html = textHtml)
        } else if (!textPlain.isNullOrEmpty()) {
            Text(
                text = parseAnsiEscapeCodes(textPlain),
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = OnSurfaceDark,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            )
        }
    }
}

@Composable
private fun Base64ImageView(base64Str: String, isZoomed: Boolean) {
    val bitmap = remember(base64Str) {
        try {
            val bytes = Base64.decode(base64Str, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = "Notebook Plot",
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isZoomed) Modifier.wrapContentHeight() else Modifier.heightIn(max = 280.dp))
                .clip(RoundedCornerShape(6.dp))
        )
    } else {
        Text("Unable to decode image output", color = ErrorRose, fontSize = 12.sp)
    }
}

@Composable
private fun HtmlTableRenderer(html: String) {
    val rows = remember(html) { parseSimpleHtmlTable(html) }

    if (rows.isEmpty()) {
        Text(html, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .border(1.dp, OutlineDark, RoundedCornerShape(4.dp))
    ) {
        rows.forEachIndexed { rowIndex, rowCells ->
            val isHeader = rowIndex == 0
            Row(
                modifier = Modifier
                    .background(if (isHeader) SurfaceVariantDark else if (rowIndex % 2 == 0) SurfaceDark else Color(0xFF1E1E22))
                    .padding(vertical = 4.dp, horizontal = 2.dp)
            ) {
                rowCells.forEach { cellText ->
                    Text(
                        text = cellText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                        color = if (isHeader) PythonBlue else OnSurfaceDark,
                        modifier = Modifier
                            .widthIn(min = 70.dp, max = 160.dp)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

private fun parseSimpleHtmlTable(html: String): List<List<String>> {
    val result = mutableListOf<List<String>>()
    val trRegex = Regex("<tr[^>]*>(.*?)</tr>", RegexOption.DOT_MATCHES_ALL)
    val thtdRegex = Regex("<t[hd][^>]*>(.*?)</t[hd]>", RegexOption.DOT_MATCHES_ALL)

    for (trMatch in trRegex.findAll(html)) {
        val rowContent = trMatch.groupValues[1]
        val cells = mutableListOf<String>()
        for (cellMatch in thtdRegex.findAll(rowContent)) {
            val text = cellMatch.groupValues[1].replace(Regex("<[^>]*>"), "").trim()
            cells.add(text)
        }
        if (cells.isNotEmpty()) {
            result.add(cells)
        }
    }
    return result
}

fun parseAnsiEscapeCodes(input: String): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        val len = input.length
        var currentColor: Color? = null
        var isBold = false

        while (i < len) {
            if (input[i] == '\u001b' && i + 1 < len && input[i + 1] == '[') {
                val mIndex = input.indexOf('m', i)
                if (mIndex != -1 && mIndex - i <= 12) {
                    val codeStr = input.substring(i + 2, mIndex)
                    val codes = codeStr.split(";").mapNotNull { it.toIntOrNull() }
                    for (c in codes) {
                        when (c) {
                            0 -> { currentColor = null; isBold = false }
                            1 -> isBold = true
                            31 -> currentColor = Color(0xFFF87171) // Red
                            32 -> currentColor = Color(0xFF4ADE80) // Green
                            33 -> currentColor = Color(0xFFFBBF24) // Yellow
                            34 -> currentColor = Color(0xFF60A5FA) // Blue
                            35 -> currentColor = Color(0xFFC084FC) // Magenta
                            36 -> currentColor = Color(0xFF38BDF8) // Cyan
                            37 -> currentColor = Color(0xFFE2E8F0) // White
                        }
                    }
                    i = mIndex + 1
                    continue
                }
            }

            val nextAnsi = input.indexOf('\u001b', i).let { if (it == -1) len else it }
            val chunk = input.substring(i, nextAnsi)
            if (currentColor != null || isBold) {
                addStyle(
                    SpanStyle(
                        color = currentColor ?: OnSurfaceDark,
                        fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
                    ),
                    length,
                    length + chunk.length
                )
            }
            append(chunk)
            i = nextAnsi
        }
    }
}
