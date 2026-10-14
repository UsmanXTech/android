package com.example.localjupyter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.localjupyter.model.Cell
import com.example.ui.theme.*

@Composable
fun MarkdownCellView(
    cell: Cell,
    onSourceChange: (String) -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isEditing by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("cell_markdown_${cell.id}"),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(10.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(if (isEditing) PythonBlue else OutlineDark)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = PythonBlue.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "Markdown",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = PythonBlue,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onMoveUp, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move Up", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onMoveDown, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move Down", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { isEditing = !isEditing }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = if (isEditing) Icons.Default.Check else Icons.Default.Edit,
                            contentDescription = if (isEditing) "Render Markdown" else "Edit Markdown",
                            tint = if (isEditing) ExecutionGreen else OnSurfaceVariantDark,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Cell", tint = ErrorRose, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isEditing) {
                OutlinedTextField(
                    value = cell.source,
                    onValueChange = onSourceChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("markdown_input_${cell.id}"),
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceDark),
                    placeholder = { Text("Enter Markdown text...", color = OnSurfaceVariantDark) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PythonBlue,
                        unfocusedBorderColor = OutlineDark,
                        focusedContainerColor = SurfaceVariantDark,
                        unfocusedContainerColor = SurfaceVariantDark
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isEditing = true }
                        .padding(vertical = 4.dp)
                ) {
                    RenderedMarkdown(content = cell.source)
                }
            }
        }
    }
}

@Composable
private fun RenderedMarkdown(content: String) {
    if (content.isBlank()) {
        Text("Empty Markdown cell. Tap to edit.", color = OnSurfaceVariantDark, fontStyle = FontStyle.Italic, fontSize = 13.sp)
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val lines = content.lines()
        var inCodeBlock = false
        val codeBuffer = mutableListOf<String>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("```")) {
                if (inCodeBlock) {
                    // Close block
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF141416),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, OutlineDark, RoundedCornerShape(6.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = codeBuffer.joinToString("\n"),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = OnSurfaceDark
                        )
                    }
                    codeBuffer.clear()
                    inCodeBlock = false
                } else {
                    inCodeBlock = true
                }
                continue
            }

            if (inCodeBlock) {
                codeBuffer.add(line)
                continue
            }

            when {
                trimmed.startsWith("# ") -> {
                    Text(
                        text = trimmed.removePrefix("# ").trim(),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = PythonBlue
                        )
                    )
                }
                trimmed.startsWith("## ") -> {
                    Text(
                        text = trimmed.removePrefix("## ").trim(),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = JupyterOrange
                        )
                    )
                }
                trimmed.startsWith("### ") -> {
                    Text(
                        text = trimmed.removePrefix("### ").trim(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = OnSurfaceDark
                        )
                    )
                }
                trimmed.startsWith("* ") || trimmed.startsWith("- ") -> {
                    Row(modifier = Modifier.padding(start = 8.dp), verticalAlignment = Alignment.Top) {
                        Text("•", color = JupyterOrange, modifier = Modifier.padding(end = 6.dp))
                        Text(
                            text = trimmed.substring(2).trim(),
                            style = MaterialTheme.typography.bodyMedium.copy(color = OnSurfaceDark)
                        )
                    }
                }
                else -> {
                    Text(
                        text = trimmed,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = OnSurfaceDark,
                            lineHeight = 20.sp
                        )
                    )
                }
            }
        }
    }
}
