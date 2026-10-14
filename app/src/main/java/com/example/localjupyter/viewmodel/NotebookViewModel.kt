package com.example.localjupyter.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.localjupyter.data.DraftManager
import com.example.localjupyter.data.NotebookMetadataItem
import com.example.localjupyter.data.SampleNotebookRepository
import com.example.localjupyter.kernel.JupyterKernelClient
import com.example.localjupyter.kernel.LocalPythonInterpreter
import com.example.localjupyter.model.*
import com.example.localjupyter.parser.NotebookSerializer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class NotebookViewModel(application: Application) : AndroidViewModel(application) {

    private val draftManager = DraftManager(application)
    val interpreter = LocalPythonInterpreter()
    val kernelClient = JupyterKernelClient(interpreter, viewModelScope)

    val kernelStatus: StateFlow<KernelStatus> = interpreter.status

    private val _notebook = MutableStateFlow(Notebook())
    val notebook: StateFlow<Notebook> = _notebook.asStateFlow()

    private val _notebookId = MutableStateFlow(UUID.randomUUID().toString())
    val notebookId: StateFlow<String> = _notebookId.asStateFlow()

    private val _notebookTitle = MutableStateFlow("Untitled.ipynb")
    val notebookTitle: StateFlow<String> = _notebookTitle.asStateFlow()

    private val _autosaveStatus = MutableStateFlow("Saved")
    val autosaveStatus: StateFlow<String> = _autosaveStatus.asStateFlow()

    private val _activeCellId = MutableStateFlow<String?>(null)
    val activeCellId: StateFlow<String?> = _activeCellId.asStateFlow()

    private val _recentNotebooks = MutableStateFlow<List<NotebookMetadataItem>>(emptyList())
    val recentNotebooks: StateFlow<List<NotebookMetadataItem>> = _recentNotebooks.asStateFlow()

    private var autosaveJob: Job? = null

    init {
        loadRecents()
        // Start with Welcome notebook
        val welcome = SampleNotebookRepository.getSamples().first()
        loadNotebook(welcome.second, welcome.first, UUID.randomUUID().toString())
    }

    fun loadRecents() {
        _recentNotebooks.value = draftManager.getRecentNotebooks()
    }

    fun loadNotebook(nb: Notebook, title: String, id: String = UUID.randomUUID().toString()) {
        _notebookId.value = id
        _notebookTitle.value = title
        _notebook.value = nb
        _activeCellId.value = nb.cells.firstOrNull()?.id
        scheduleAutosave()
    }

    fun newNotebook() {
        val newId = UUID.randomUUID().toString()
        val emptyNb = Notebook(
            cells = listOf(
                Cell(
                    id = UUID.randomUUID().toString(),
                    cellType = CellType.CODE,
                    source = "# Welcome to a new notebook\nimport numpy as np\n"
                )
            )
        )
        loadNotebook(emptyNb, "Untitled_${System.currentTimeMillis() % 1000}.ipynb", newId)
    }

    fun updateTitle(newTitle: String) {
        _notebookTitle.value = newTitle
        scheduleAutosave()
    }

    fun setActiveCell(cellId: String) {
        _activeCellId.value = cellId
    }

    fun updateCellSource(cellId: String, newSource: String) {
        val updated = _notebook.value.cells.map { cell ->
            if (cell.id == cellId) cell.copy(source = newSource) else cell
        }
        _notebook.value = _notebook.value.copy(cells = updated)
        scheduleAutosave()
    }

    fun insertQuickToken(token: String) {
        val targetId = _activeCellId.value ?: _notebook.value.cells.firstOrNull()?.id ?: return
        val currentCells = _notebook.value.cells.map { cell ->
            if (cell.id == targetId) {
                cell.copy(source = cell.source + token)
            } else cell
        }
        _notebook.value = _notebook.value.copy(cells = currentCells)
        scheduleAutosave()
    }

    fun addCell(cellType: CellType, afterCellId: String? = null) {
        val newCell = Cell(
            id = UUID.randomUUID().toString(),
            cellType = cellType,
            source = if (cellType == CellType.MARKDOWN) "# Markdown Section\n" else ""
        )
        val cells = _notebook.value.cells.toMutableList()
        val targetIndex = if (afterCellId != null) {
            cells.indexOfFirst { it.id == afterCellId }.let { if (it == -1) cells.size else it + 1 }
        } else {
            val activeIdx = cells.indexOfFirst { it.id == _activeCellId.value }
            if (activeIdx != -1) activeIdx + 1 else cells.size
        }
        cells.add(targetIndex, newCell)
        _notebook.value = _notebook.value.copy(cells = cells)
        _activeCellId.value = newCell.id
        scheduleAutosave()
    }

    fun deleteCell(cellId: String) {
        val cells = _notebook.value.cells.filterNot { it.id == cellId }
        val finalCells = if (cells.isEmpty()) {
            listOf(Cell(id = UUID.randomUUID().toString(), cellType = CellType.CODE, source = ""))
        } else cells
        _notebook.value = _notebook.value.copy(cells = finalCells)
        if (_activeCellId.value == cellId) {
            _activeCellId.value = finalCells.firstOrNull()?.id
        }
        scheduleAutosave()
    }

    fun duplicateCell(cellId: String) {
        val cells = _notebook.value.cells.toMutableList()
        val idx = cells.indexOfFirst { it.id == cellId }
        if (idx != -1) {
            val original = cells[idx]
            val duplicate = original.copy(
                id = UUID.randomUUID().toString(),
                executionCount = null,
                outputs = emptyList()
            )
            cells.add(idx + 1, duplicate)
            _notebook.value = _notebook.value.copy(cells = cells)
            _activeCellId.value = duplicate.id
            scheduleAutosave()
        }
    }

    fun moveCellUp(cellId: String) {
        val cells = _notebook.value.cells.toMutableList()
        val idx = cells.indexOfFirst { it.id == cellId }
        if (idx > 0) {
            val item = cells.removeAt(idx)
            cells.add(idx - 1, item)
            _notebook.value = _notebook.value.copy(cells = cells)
            scheduleAutosave()
        }
    }

    fun moveCellDown(cellId: String) {
        val cells = _notebook.value.cells.toMutableList()
        val idx = cells.indexOfFirst { it.id == cellId }
        if (idx != -1 && idx < cells.size - 1) {
            val item = cells.removeAt(idx)
            cells.add(idx + 1, item)
            _notebook.value = _notebook.value.copy(cells = cells)
            scheduleAutosave()
        }
    }

    fun clearCellOutput(cellId: String) {
        val cells = _notebook.value.cells.map { cell ->
            if (cell.id == cellId) cell.copy(outputs = emptyList(), executionCount = null) else cell
        }
        _notebook.value = _notebook.value.copy(cells = cells)
        scheduleAutosave()
    }

    fun runCell(cellId: String) {
        val cell = _notebook.value.cells.find { it.id == cellId } ?: return
        if (cell.cellType != CellType.CODE) return

        _activeCellId.value = cellId

        viewModelScope.launch {
            // Mark cell as running and clear previous outputs
            updateCellState(cellId) { it.copy(isRunning = true, outputs = emptyList()) }

            kernelClient.sendExecuteRequest(cell.source) { reply ->
                val replyContent = reply.content
                updateCellState(cellId) {
                    it.copy(
                        isRunning = false,
                        executionCount = replyContent.executionCount
                    )
                }
                scheduleAutosave()
            }
        }
    }

    fun runAllCells() {
        viewModelScope.launch {
            val codeCells = _notebook.value.cells.filter { it.cellType == CellType.CODE }
            for (cell in codeCells) {
                runCell(cell.id)
                delay(100) // Brief delay between cell executions to allow queue processing
            }
        }
    }

    fun restartKernel() {
        viewModelScope.launch {
            interpreter.restart()
            // Reset execution counts across all cells
            val cleared = _notebook.value.cells.map { it.copy(isRunning = false) }
            _notebook.value = _notebook.value.copy(cells = cleared)
        }
    }

    fun interruptKernel() {
        viewModelScope.launch {
            interpreter.interrupt()
            val resetRunning = _notebook.value.cells.map { if (it.isRunning) it.copy(isRunning = false) else it }
            _notebook.value = _notebook.value.copy(cells = resetRunning)
        }
    }

    fun exportNotebookJson(): String {
        return NotebookSerializer.serialize(_notebook.value)
    }

    fun loadNotebookFromJson(json: String, title: String) {
        try {
            val parsed = NotebookSerializer.parse(json)
            loadNotebook(parsed, title, UUID.randomUUID().toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun deleteRecentNotebook(id: String) {
        draftManager.deleteDraft(id)
        loadRecents()
    }

    private fun updateCellState(cellId: String, transform: (Cell) -> Cell) {
        val updated = _notebook.value.cells.map { cell ->
            if (cell.id == cellId) transform(cell) else cell
        }
        _notebook.value = _notebook.value.copy(cells = updated)
    }

    private fun scheduleAutosave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            _autosaveStatus.value = "Saving..."
            delay(1000)
            draftManager.saveDraft(_notebookId.value, _notebookTitle.value, _notebook.value)
            _autosaveStatus.value = "Saved"
            loadRecents()
        }
    }
}
