package com.example.localjupyter.data

import android.content.Context
import com.example.localjupyter.model.Notebook
import com.example.localjupyter.parser.NotebookSerializer
import java.io.File

data class NotebookMetadataItem(
    val id: String,
    val title: String,
    val lastModified: Long,
    val cellCount: Int,
    val fileName: String
)

class DraftManager(private val context: Context) {

    private val draftsDir = File(context.filesDir, "drafts").apply { mkdirs() }

    fun saveDraft(id: String, title: String, notebook: Notebook) {
        try {
            val file = File(draftsDir, "$id.ipynb")
            val json = NotebookSerializer.serialize(notebook)
            file.writeText(json)
            updateIndex(id, title, notebook.cells.size, file.name)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadDraft(id: String): Notebook? {
        val file = File(draftsDir, "$id.ipynb")
        if (!file.exists()) return null
        return try {
            NotebookSerializer.parse(file.readText())
        } catch (e: Exception) {
            null
        }
    }

    fun getRecentNotebooks(): List<NotebookMetadataItem> {
        val indexFile = File(draftsDir, "index.txt")
        if (!indexFile.exists()) return emptyList()
        return try {
            indexFile.readLines().mapNotNull { line ->
                val parts = line.split("|")
                if (parts.size >= 5) {
                    NotebookMetadataItem(
                        id = parts[0],
                        title = parts[1],
                        lastModified = parts[2].toLongOrNull() ?: 0L,
                        cellCount = parts[3].toIntOrNull() ?: 0,
                        fileName = parts[4]
                    )
                } else null
            }.sortedByDescending { it.lastModified }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun deleteDraft(id: String) {
        val file = File(draftsDir, "$id.ipynb")
        if (file.exists()) file.delete()
        val indexFile = File(draftsDir, "index.txt")
        if (indexFile.exists()) {
            val lines = indexFile.readLines().filterNot { it.startsWith("$id|") }
            indexFile.writeText(lines.joinToString("\n"))
        }
    }

    private fun updateIndex(id: String, title: String, cellCount: Int, fileName: String) {
        val items = getRecentNotebooks().filterNot { it.id == id }.toMutableList()
        items.add(
            0,
            NotebookMetadataItem(
                id = id,
                title = title,
                lastModified = System.currentTimeMillis(),
                cellCount = cellCount,
                fileName = fileName
            )
        )
        val indexFile = File(draftsDir, "index.txt")
        val lines = items.take(25).joinToString("\n") {
            "${it.id}|${it.title}|${it.lastModified}|${it.cellCount}|${it.fileName}"
        }
        indexFile.writeText(lines)
    }
}
