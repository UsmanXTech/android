package com.example.localjupyter.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.localjupyter.data.NotebookMetadataItem
import com.example.localjupyter.data.SampleNotebookRepository
import com.example.localjupyter.viewmodel.NotebookViewModel
import com.example.ui.theme.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: NotebookViewModel,
    onNavigateToEditor: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val recentNotebooks by viewModel.recentNotebooks.collectAsState()
    var showRuntimeDialog by remember { mutableStateOf(false) }

    // SAF Document Picker for opening .ipynb
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    val reader = BufferedReader(InputStreamReader(stream))
                    val content = reader.readText()
                    val fileName = it.lastPathSegment?.substringAfterLast("/") ?: "Imported.ipynb"
                    viewModel.loadNotebookFromJson(content, fileName)
                    onNavigateToEditor()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = JupyterOrange,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("🪐", fontSize = 16.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "LocalJupyter",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp
                            )
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showRuntimeDialog = true },
                        modifier = Modifier.testTag("action_runtime_info")
                    ) {
                        Icon(Icons.Default.Info, contentDescription = "Runtime Info", tint = OnSurfaceVariantDark)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        },
        containerColor = BackgroundDark,
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Status Banner
            item {
                Spacer(modifier = Modifier.height(4.dp))
                HeroBanner()
            }

            // Quick Actions: New & Open
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.newNotebook()
                            onNavigateToEditor()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = JupyterOrange),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("btn_new_notebook")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "New Notebook")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("New Notebook", fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = {
                            openDocumentLauncher.launch(arrayOf("*/*", "application/json", "application/x-ipynb+json"))
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PythonBlue),
                        border = ButtonDefaults.outlinedButtonBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(PythonBlue)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("btn_open_ipynb")
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = "Open .ipynb")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open .ipynb", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Interactive Samples Section
            item {
                Text(
                    text = "Interactive Tutorials & Samples",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceDark
                    )
                )
            }

            items(SampleNotebookRepository.getSamples()) { (title, sampleNotebook) ->
                SampleNotebookCard(
                    title = title,
                    cellCount = sampleNotebook.cells.size,
                    onClick = {
                        viewModel.loadNotebook(sampleNotebook, title)
                        onNavigateToEditor()
                    }
                )
            }

            // Recent Drafts Section
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Notebooks & Drafts",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = OnSurfaceDark
                        )
                    )
                    Text(
                        text = "${recentNotebooks.size} saved",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariantDark
                    )
                }
            }

            if (recentNotebooks.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = SurfaceDark,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = "No Drafts",
                                tint = OnSurfaceVariantDark,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No local drafts yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = OnSurfaceVariantDark
                            )
                        }
                    }
                }
            } else {
                items(recentNotebooks, key = { it.id }) { item ->
                    RecentNotebookItem(
                        item = item,
                        onClick = {
                            val loaded = viewModel.loadNotebookFromJson(
                                json = com.example.localjupyter.parser.NotebookSerializer.serialize(
                                    com.example.localjupyter.data.DraftManager(context).loadDraft(item.id) ?: return@RecentNotebookItem
                                ),
                                title = item.title
                            )
                            onNavigateToEditor()
                        },
                        onDelete = {
                            viewModel.deleteRecentNotebook(item.id)
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showRuntimeDialog) {
        RuntimeInfoDialog(onDismiss = { showRuntimeDialog = false })
    }
}

@Composable
private fun HeroBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SurfaceDark,
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(OutlineDark)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = ExecutionGreen.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = ExecutionGreen,
                            modifier = Modifier.size(8.dp)
                        ) {}
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Offline Python 3.11",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ExecutionGreen
                        )
                    }
                }

                Text(
                    text = "No Cloud • No Ads • Zero Termux",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariantDark
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Interactive Scientific Computing",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "NumPy matrix arrays, Matplotlib high-res bitmap plots, and Pandas tabular DataFrames running locally on Android.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = OnSurfaceVariantDark,
                    lineHeight = 20.sp
                )
            )
        }
    }
}

@Composable
private fun SampleNotebookCard(
    title: String,
    cellCount: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("sample_${title.take(10)}"),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(10.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(OutlineDark)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SurfaceVariantDark,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = "Sample Notebook",
                            tint = JupyterOrange,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = OnSurfaceDark
                        )
                    )
                    Text(
                        text = "$cellCount cells • Ready to execute",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariantDark
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Open",
                tint = OnSurfaceVariantDark
            )
        }
    }
}

@Composable
private fun RecentNotebookItem(
    item: NotebookMetadataItem,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateStr = remember(item.lastModified) {
        val sdf = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
        sdf.format(Date(item.lastModified))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("recent_${item.id}"),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(10.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(OutlineDark)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = "Notebook",
                    tint = PythonBlue,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = OnSurfaceDark
                        )
                    )
                    Text(
                        text = "$dateStr • ${item.cellCount} cells",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariantDark
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete Draft",
                    tint = OnSurfaceVariantDark,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun RuntimeInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "LocalJupyter System & Runtime",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RuntimeSpecRow("Runtime", "Embedded Python 3.11")
                RuntimeSpecRow("Kernel Spec", "Python 3 (LocalJupyter)")
                RuntimeSpecRow("Protocol", "Jupyter Messaging v5.4")
                RuntimeSpecRow("NumPy", "v1.26 (Vector & NDArray)")
                RuntimeSpecRow("Matplotlib", "v3.8 (Canvas PNG renderer)")
                RuntimeSpecRow("pandas", "v2.2 (DataFrame & HTML)")
                RuntimeSpecRow("Storage", "Storage Access Framework")
                RuntimeSpecRow("Network", "100% Offline (Zero external telemetry)")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = JupyterOrange)
            }
        },
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun RuntimeSpecRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = OnSurfaceVariantDark, fontSize = 13.sp)
        Text(value, color = OnSurfaceDark, fontWeight = FontWeight.Medium, fontSize = 13.sp)
    }
}
