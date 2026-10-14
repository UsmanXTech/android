package com.example.localjupyter.parser

import com.example.localjupyter.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Jupyter Notebook Format v4 JSON Parser and Serializer.
 * Supports round-trip serialization preserving unknown metadata,
 * execution counts, and multi-format MIME bundles.
 */
object NotebookSerializer {

    fun parse(jsonString: String): Notebook {
        val root = JSONObject(jsonString)
        val nbformat = root.optInt("nbformat", 4)
        val nbformatMinor = root.optInt("nbformat_minor", 5)

        // Metadata
        val metadataObj = root.optJSONObject("metadata") ?: JSONObject()
        val kernelSpecObj = metadataObj.optJSONObject("kernelspec")
        val kernelSpec = if (kernelSpecObj != null) {
            KernelSpec(
                name = kernelSpecObj.optString("name", "python3"),
                displayName = kernelSpecObj.optString("display_name", "Python 3"),
                language = kernelSpecObj.optString("language", "python")
            )
        } else KernelSpec()

        val langInfoObj = metadataObj.optJSONObject("language_info")
        val languageInfo = if (langInfoObj != null) {
            LanguageInfo(
                name = langInfoObj.optString("name", "python"),
                version = langInfoObj.optString("version", "3.11.0"),
                mimetype = langInfoObj.optString("mimetype", "text/x-python"),
                fileExtension = langInfoObj.optString("file_extension", ".py")
            )
        } else LanguageInfo()

        val additionalMeta = mutableMapOf<String, Any?>()
        metadataObj.keys().forEach { k ->
            if (k != "kernelspec" && k != "language_info") {
                additionalMeta[k] = jsonValueToAny(metadataObj.get(k))
            }
        }
        val metadata = NotebookMetadata(
            kernelSpec = kernelSpec,
            languageInfo = languageInfo,
            additionalFields = additionalMeta
        )

        // Cells
        val cellsArray = root.optJSONArray("cells") ?: JSONArray()
        val cells = mutableListOf<Cell>()

        for (i in 0 until cellsArray.length()) {
            val cellObj = cellsArray.optJSONObject(i) ?: continue
            val id = cellObj.optString("id", UUID.randomUUID().toString())
            val cellTypeStr = cellObj.optString("cell_type", "code")
            val cellType = when (cellTypeStr) {
                "markdown" -> CellType.MARKDOWN
                "raw" -> CellType.RAW
                else -> CellType.CODE
            }

            val source = parseSource(cellObj.opt("source"))
            val executionCount = if (cellObj.has("execution_count") && !cellObj.isNull("execution_count")) {
                cellObj.optInt("execution_count")
            } else null

            // Cell outputs
            val outputsArray = cellObj.optJSONArray("outputs")
            val outputs = mutableListOf<CellOutput>()
            if (outputsArray != null) {
                for (j in 0 until outputsArray.length()) {
                    val outObj = outputsArray.optJSONObject(j) ?: continue
                    val outType = outObj.optString("output_type", "")
                    when (outType) {
                        "stream" -> {
                            val name = outObj.optString("name", "stdout")
                            val text = parseSource(outObj.opt("text"))
                            outputs.add(CellOutput.Stream(name, text))
                        }
                        "execute_result" -> {
                            val count = outObj.optInt("execution_count", executionCount ?: 1)
                            val dataObj = outObj.optJSONObject("data") ?: JSONObject()
                            val dataMap = jsonObjectToMap(dataObj)
                            outputs.add(CellOutput.ExecuteResult(count, dataMap))
                        }
                        "display_data" -> {
                            val dataObj = outObj.optJSONObject("data") ?: JSONObject()
                            val dataMap = jsonObjectToMap(dataObj)
                            outputs.add(CellOutput.DisplayData(dataMap))
                        }
                        "error" -> {
                            val ename = outObj.optString("ename", "Error")
                            val evalue = outObj.optString("evalue", "")
                            val tbArray = outObj.optJSONArray("traceback") ?: JSONArray()
                            val tbList = (0 until tbArray.length()).map { tbArray.getString(it) }
                            outputs.add(CellOutput.Error(ename, evalue, tbList))
                        }
                    }
                }
            }

            // Cell metadata
            val cellMetaObj = cellObj.optJSONObject("metadata") ?: JSONObject()
            val cellMeta = jsonObjectToMap(cellMetaObj)

            cells.add(
                Cell(
                    id = id,
                    cellType = cellType,
                    source = source,
                    executionCount = executionCount,
                    outputs = outputs,
                    metadata = cellMeta
                )
            )
        }

        // Unknown root fields
        val unknown = mutableMapOf<String, Any?>()
        root.keys().forEach { k ->
            if (k != "cells" && k != "metadata" && k != "nbformat" && k != "nbformat_minor") {
                unknown[k] = jsonValueToAny(root.get(k))
            }
        }

        return Notebook(
            cells = cells,
            metadata = metadata,
            nbformat = nbformat,
            nbformatMinor = nbformatMinor,
            unknownFields = unknown
        )
    }

    fun serialize(notebook: Notebook): String {
        val root = JSONObject()
        root.put("nbformat", notebook.nbformat)
        root.put("nbformat_minor", notebook.nbformatMinor)

        // Metadata
        val metaObj = JSONObject()
        val ksObj = JSONObject().apply {
            put("name", notebook.metadata.kernelSpec.name)
            put("display_name", notebook.metadata.kernelSpec.displayName)
            put("language", notebook.metadata.kernelSpec.language)
        }
        metaObj.put("kernelspec", ksObj)

        val liObj = JSONObject().apply {
            put("name", notebook.metadata.languageInfo.name)
            put("version", notebook.metadata.languageInfo.version)
            put("mimetype", notebook.metadata.languageInfo.mimetype)
            put("file_extension", notebook.metadata.languageInfo.fileExtension)
        }
        metaObj.put("language_info", liObj)

        notebook.metadata.additionalFields.forEach { (k, v) ->
            metaObj.put(k, anyToJsonValue(v))
        }
        root.put("metadata", metaObj)

        // Cells
        val cellsArray = JSONArray()
        for (cell in notebook.cells) {
            val cellObj = JSONObject()
            cellObj.put("id", cell.id)
            val typeStr = when (cell.cellType) {
                CellType.CODE -> "code"
                CellType.MARKDOWN -> "markdown"
                CellType.RAW -> "raw"
            }
            cellObj.put("cell_type", typeStr)
            cellObj.put("metadata", mapToJsonObject(cell.metadata))

            // Split lines for Jupyter convention
            val lines = cell.source.lines()
            val sourceArray = JSONArray()
            lines.forEachIndexed { idx, line ->
                val end = if (idx < lines.size - 1) "$line\n" else line
                sourceArray.put(end)
            }
            cellObj.put("source", sourceArray)

            if (cell.cellType == CellType.CODE) {
                cellObj.put("execution_count", cell.executionCount ?: JSONObject.NULL)
                val outputsArray = JSONArray()
                for (out in cell.outputs) {
                    val outObj = JSONObject()
                    when (out) {
                        is CellOutput.Stream -> {
                            outObj.put("output_type", "stream")
                            outObj.put("name", out.name)
                            outObj.put("text", out.text)
                        }
                        is CellOutput.ExecuteResult -> {
                            outObj.put("output_type", "execute_result")
                            outObj.put("execution_count", out.executionCount)
                            outObj.put("data", mapToJsonObject(out.data))
                            outObj.put("metadata", mapToJsonObject(out.metadata))
                        }
                        is CellOutput.DisplayData -> {
                            outObj.put("output_type", "display_data")
                            outObj.put("data", mapToJsonObject(out.data))
                            outObj.put("metadata", mapToJsonObject(out.metadata))
                        }
                        is CellOutput.Error -> {
                            outObj.put("output_type", "error")
                            outObj.put("ename", out.ename)
                            outObj.put("evalue", out.evalue)
                            val tbArr = JSONArray()
                            out.traceback.forEach { tbArr.put(it) }
                            outObj.put("traceback", tbArr)
                        }
                    }
                    outputsArray.put(outObj)
                }
                cellObj.put("outputs", outputsArray)
            }

            cellsArray.put(cellObj)
        }
        root.put("cells", cellsArray)

        // Unknown fields
        notebook.unknownFields.forEach { (k, v) ->
            root.put(k, anyToJsonValue(v))
        }

        return root.toString(2)
    }

    private fun parseSource(sourceObj: Any?): String {
        return when (sourceObj) {
            is String -> sourceObj
            is JSONArray -> (0 until sourceObj.length()).joinToString("") { sourceObj.getString(it) }
            is List<*> -> sourceObj.joinToString("") { it.toString() }
            else -> ""
        }
    }

    private fun jsonObjectToMap(obj: JSONObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            map[key] = jsonValueToAny(obj.get(key))
        }
        return map
    }

    private fun jsonValueToAny(value: Any?): Any? {
        return when (value) {
            JSONObject.NULL, null -> null
            is JSONObject -> jsonObjectToMap(value)
            is JSONArray -> (0 until value.length()).map { jsonValueToAny(value.get(it)) }
            else -> value
        }
    }

    private fun mapToJsonObject(map: Map<String, Any?>): JSONObject {
        val obj = JSONObject()
        map.forEach { (k, v) ->
            obj.put(k, anyToJsonValue(v))
        }
        return obj
    }

    private fun anyToJsonValue(v: Any?): Any {
        return when (v) {
            null -> JSONObject.NULL
            is Map<*, *> -> {
                val obj = JSONObject()
                v.forEach { (k, valItem) -> obj.put(k.toString(), anyToJsonValue(valItem)) }
                obj
            }
            is List<*> -> {
                val arr = JSONArray()
                v.forEach { arr.put(anyToJsonValue(it)) }
                arr
            }
            else -> v
        }
    }
}
