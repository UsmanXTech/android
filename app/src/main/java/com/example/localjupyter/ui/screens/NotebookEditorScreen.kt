package com.example.localjupyter.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.localjupyter.model.CellType
import com.example.localjupyter.model.KernelStatus
import com.example.localjupyter.ui.components.CodeCellView
import com.example.localjupyter.ui.components.KeyboardAccessoryBar
import com.example.localjupyter.ui.components.MarkdownCellView
import com.example.localjupyter.viewmodel.NotebookViewModel
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookEditorScreen(
    viewModel: NotebookViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val notebook by viewModel.notebook.collectAsState()
    val title by viewModel.notebookTitle.collectAsState()
    val kernelStatus by viewModel.kernelStatus.collectAsState()
    val autosaveStatus by viewModel.autosaveStatus.collectAsState()
    val activeCellId by viewModel.activeCellId.collectAsState()

    var showTitleDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    // BackHandler to safely return to Home
    BackHandler {
        onNavigateBack()
    }

    // SAF Document Saver for exporting .ipynb
    val saveDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/x-ipynb+json")
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { stream ->
                    val json = viewModel.exportNotebookJson()
                    stream.write(json.toByteArray(Charsets.UTF_8))
                    Toast.makeText(context, "Saved to ${it.lastPathSegment}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to save: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        modifier = Modifier
                            .clickable { showTitleDialog = true }
                            .padding(vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = OnSurfaceDark
                                ),
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Title",
                                tint = OnSurfaceVariantDark,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        // Kernel & Save Status Badge Row
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            KernelStatusPill(status = kernelStatus)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "•  $autosaveStatus",
                                style = MaterialTheme.typography.labelSmall,
                                color = OnSurfaceVariantDark
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back to Home", tint = OnSurfaceDark)
                    }
                },
                actions = {
                    // Run All Button
                    IconButton(
                        onClick = { viewModel.runAllCells() },
                        modifier = Modifier.testTag("action_run_all")
                    ) {
                        Icon(Icons.Default.FastForward, contentDescription = "Run All", tint = JupyterOrange)
                    }

                    // Restart Kernel Button
                    IconButton(
                        onClick = { viewModel.restartKernel() },
                        modifier = Modifier.testTag("action_restart_kernel")
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = "Restart Kernel", tint = PythonBlue)
                    }

                    // Save / Export (.ipynb)
                    IconButton(
                        onClick = {
                            val defaultName = if (title.endsWith(".ipynb")) title else "$title.ipynb"
                            saveDocumentLauncher.launch(defaultName)
                        },
                        modifier = Modifier.testTag("action_save_as")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Save / Export As", tint = OnSurfaceDark)
                    }

                    // Overflow Menu
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false },
                            modifier = Modifier.background(SurfaceDark)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Interrupt Kernel", color = OnSurfaceDark) },
                                onClick = {
                                    viewModel.interruptKernel()
                                    showMoreMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Stop, contentDescription = null, tint = ErrorRose)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Clear All Outputs", color = OnSurfaceDark) },
                                onClick = {
                                    notebook.cells.forEach { viewModel.clearCellOutput(it.id) }
                                    showMoreMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.ClearAll, contentDescription = null, tint = OnSurfaceVariantDark)
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        },
        bottomBar = {
            KeyboardAccessoryBar(
                onInsertText = { viewModel.insertQuickToken(it) },
                onRunCurrentCell = {
                    activeCellId?.let { viewModel.runCell(it) }
                }
            )
        },
        containerColor = BackgroundDark,
        modifier = modifier
    ) { innerPadding ->
        val listState = rememberLazyListState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Quick Add Cell Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { viewModel.addCell(CellType.CODE) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = JupyterOrange),
                    modifier = Modifier.testTag("btn_add_code_cell")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+ Code", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = { viewModel.addCell(CellType.MARKDOWN) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PythonBlue),
                    modifier = Modifier.testTag("btn_add_markdown_cell")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+ Markdown", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${notebook.cells.size} cells",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariantDark
                )
            }

            // Virtualized LazyColumn for high-performance scrolling
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }

                items(notebook.cells, key = { it.id }) { cell ->
                    when (cell.cellType) {
                        CellType.CODE -> {
                            CodeCellView(
                                cell = cell,
                                onSourceChange = { viewModel.updateCellSource(cell.id, it) },
                                onRun = { viewModel.runCell(cell.id) },
                                onDelete = { viewModel.deleteCell(cell.id) },
                                onDuplicate = { viewModel.duplicateCell(cell.id) },
                                onMoveUp = { viewModel.moveCellUp(cell.id) },
                                onMoveDown = { viewModel.moveCellDown(cell.id) },
                                onClearOutput = { viewModel.clearCellOutput(cell.id) },
                                modifier = Modifier.clickable { viewModel.setActiveCell(cell.id) }
                            )
                        }
                        CellType.MARKDOWN, CellType.RAW -> {
                            MarkdownCellView(
                                cell = cell,
                                onSourceChange = { viewModel.updateCellSource(cell.id, it) },
                                onDelete = { viewModel.deleteCell(cell.id) },
                                onMoveUp = { viewModel.moveCellUp(cell.id) },
                                onMoveDown = { viewModel.moveCellDown(cell.id) },
                                modifier = Modifier.clickable { viewModel.setActiveCell(cell.id) }
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(60.dp))
                }
            }
        }
    }

    // Rename dialog
    if (showTitleDialog) {
        var tempTitle by remember { mutableStateOf(title) }
        AlertDialog(
            onDismissRequest = { showTitleDialog = false },
            title = { Text("Rename Notebook") },
            text = {
                OutlinedTextField(
                    value = tempTitle,
                    onValueChange = { tempTitle = it },
                    singleLine = true,
                    label = { Text("Notebook Name") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val finalName = if (tempTitle.endsWith(".ipynb")) tempTitle else "$tempTitle.ipynb"
                        viewModel.updateTitle(finalName)
                        showTitleDialog = false
                    }
                ) {
                    Text("Rename", color = JupyterOrange)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTitleDialog = false }) {
                    Text("Cancel", color = OnSurfaceVariantDark)
                }
            },
            containerColor = SurfaceDark
        )
    }
}

@Composable
private fun KernelStatusPill(status: KernelStatus) {
    val (dotColor, label) = when (status) {
        KernelStatus.IDLE -> ExecutionGreen to "Idle"
        KernelStatus.BUSY -> JupyterOrange to "Busy"
        KernelStatus.STARTING -> PythonBlue to "Starting"
        KernelStatus.RESTARTING -> PythonGold to "Restarting"
        KernelStatus.DEAD -> ErrorRose to "Dead"
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = dotColor.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = dotColor,
                modifier = Modifier.size(6.dp)
            ) {}
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = dotColor
            )
        }
    }
}
