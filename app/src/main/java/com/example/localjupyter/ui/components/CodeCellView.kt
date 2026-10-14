package com.example.localjupyter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.localjupyter.model.Cell
import com.example.ui.theme.*

@Composable
fun CodeCellView(
    cell: Cell,
    onSourceChange: (String) -> Unit,
    onRun: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onClearOutput: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("cell_code_${cell.id}"),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(10.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(if (cell.isRunning) JupyterOrange else OutlineDark)
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Header bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // In [X]: Badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (cell.isRunning) JupyterOrange.copy(alpha = 0.2f) else SurfaceVariantDark,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = if (cell.isRunning) "In [*]:" else if (cell.executionCount != null) "In [${cell.executionCount}]:" else "In [ ]:",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (cell.isRunning) JupyterOrange else PythonBlue,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (cell.isRunning) {
                        CircularProgressIndicator(
                            color = JupyterOrange,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // Cell Actions
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onRun,
                        enabled = !cell.isRunning,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("run_button_${cell.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Run Cell",
                            tint = JupyterOrange,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = onMoveUp, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move Up", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onMoveDown, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move Down", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDuplicate, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate Cell", modifier = Modifier.size(16.dp))
                    }
                    if (cell.outputs.isNotEmpty()) {
                        IconButton(onClick = onClearOutput, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = "Clear Output", modifier = Modifier.size(16.dp))
                        }
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Cell", tint = ErrorRose, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Code Editor Box with Line Numbers & Syntax Highlighting
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF141416))
                    .border(1.dp, Color(0xFF27272A), RoundedCornerShape(6.dp))
                    .padding(8.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    // Line numbers
                    val lineCount = cell.source.lines().size.coerceAtLeast(1)
                    val lineNumbersText = (1..lineCount).joinToString("\n")
                    Text(
                        text = lineNumbersText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = Color(0xFF52525B),
                        modifier = Modifier.padding(end = 12.dp)
                    )

                    // Code Input
                    BasicTextField(
                        value = cell.source,
                        onValueChange = onSourceChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("code_input_${cell.id}"),
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = OnSurfaceDark
                        ),
                        cursorBrush = SolidColor(JupyterOrange),
                        decorationBox = { innerTextField ->
                            if (cell.source.isEmpty()) {
                                Text(
                                    text = "# Enter Python code...",
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp,
                                        color = SyntaxComment
                                    )
                                )
                            }
                            innerTextField()
                        }
                    )
                }
            }

            // Rich Output View
            if (cell.outputs.isNotEmpty()) {
                RichOutputView(outputs = cell.outputs)
            }
        }
    }
}
