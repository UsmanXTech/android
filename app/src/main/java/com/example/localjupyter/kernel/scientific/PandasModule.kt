package com.example.localjupyter.kernel.scientific

import kotlin.math.sqrt

/**
 * Pandas implementation for Android LocalJupyter.
 * Provides Series, DataFrame, statistical aggregations, CSV parsing,
 * and rich HTML table rendering for Jupyter MIME bundles.
 */
class PandasModule {

    class Series(
        val name: String?,
        val values: List<Any?>,
        val index: List<Any> = (0 until values.size).toList()
    ) {
        val size: Int get() = values.size

        fun mean(): Double {
            val nums = values.mapNotNull { (it as? Number)?.toDouble() }
            return if (nums.isNotEmpty()) nums.average() else 0.0
        }

        fun sum(): Double {
            val nums = values.mapNotNull { (it as? Number)?.toDouble() }
            return nums.sum()
        }

        fun min(): Double {
            val nums = values.mapNotNull { (it as? Number)?.toDouble() }
            return nums.minOrNull() ?: 0.0
        }

        fun max(): Double {
            val nums = values.mapNotNull { (it as? Number)?.toDouble() }
            return nums.maxOrNull() ?: 0.0
        }

        fun std(): Double {
            val nums = values.mapNotNull { (it as? Number)?.toDouble() }
            if (nums.isEmpty()) return 0.0
            val m = nums.average()
            val variance = nums.map { (it - m) * (it - m) }.average()
            return sqrt(variance)
        }

        override fun toString(): String {
            val sb = StringBuilder()
            for (i in values.indices) {
                sb.append(String.format(java.util.Locale.US, "%-5s %s\n", index[i], values[i]))
            }
            if (name != null) sb.append("Name: $name, ")
            sb.append("dtype: object")
            return sb.toString()
        }
    }

    class DataFrame(
        val data: Map<String, List<Any?>>,
        val index: List<Any>? = null
    ) {
        val columns: List<String> = data.keys.toList()
        val rowCount: Int = data.values.firstOrNull()?.size ?: 0
        val shape: Pair<Int, Int> = rowCount to columns.size

        val resolvedIndex: List<Any> = index ?: (0 until rowCount).toList()

        operator fun get(col: String): Series {
            val colData = data[col] ?: throw IllegalArgumentException("KeyError: '$col'")
            return Series(col, colData, resolvedIndex)
        }

        fun head(n: Int = 5): DataFrame {
            val count = kotlin.math.min(n, rowCount)
            val sliced = data.mapValues { it.value.take(count) }
            return DataFrame(sliced, resolvedIndex.take(count))
        }

        fun tail(n: Int = 5): DataFrame {
            val count = kotlin.math.min(n, rowCount)
            val sliced = data.mapValues { it.value.takeLast(count) }
            return DataFrame(sliced, resolvedIndex.takeLast(count))
        }

        fun describe(): DataFrame {
            val statsMap = mutableMapOf<String, List<Any?>>()
            val statRows = listOf("count", "mean", "std", "min", "50%", "max")
            statsMap["stat"] = statRows

            for (col in columns) {
                val nums = data[col]?.mapNotNull { (it as? Number)?.toDouble() } ?: emptyList()
                if (nums.isNotEmpty()) {
                    val count = nums.size.toDouble()
                    val mean = nums.average()
                    val variance = nums.map { (it - mean) * (it - mean) }.average()
                    val std = sqrt(variance)
                    val min = nums.minOrNull() ?: 0.0
                    val max = nums.maxOrNull() ?: 0.0
                    val sorted = nums.sorted()
                    val median = sorted[sorted.size / 2]
                    statsMap[col] = listOf(
                        count.toInt(),
                        round4(mean),
                        round4(std),
                        round4(min),
                        round4(median),
                        round4(max)
                    )
                }
            }
            return DataFrame(statsMap, statRows)
        }

        fun info(): String {
            val sb = StringBuilder()
            sb.append("<class 'pandas.core.frame.DataFrame'>\n")
            sb.append("RangeIndex: $rowCount entries, 0 to ${rowCount - 1}\n")
            sb.append("Data columns (total ${columns.size} columns):\n")
            sb.append(String.format(java.util.Locale.US, " #   Column%-12s Non-Null Count  Dtype\n", ""))
            sb.append("---  ------            --------------  -----\n")
            columns.forEachIndexed { i, col ->
                val nonNull = data[col]?.count { it != null } ?: 0
                sb.append(String.format(java.util.Locale.US, " %-3d %-17s %-15d object\n", i, col, nonNull))
            }
            sb.append("dtypes: object(${columns.size})\n")
            return sb.toString()
        }

        fun mean(): Map<String, Double> {
            val res = mutableMapOf<String, Double>()
            for (col in columns) {
                val nums = data[col]?.mapNotNull { (it as? Number)?.toDouble() } ?: emptyList()
                if (nums.isNotEmpty()) res[col] = round4(nums.average())
            }
            return res
        }

        fun sum(): Map<String, Double> {
            val res = mutableMapOf<String, Double>()
            for (col in columns) {
                val nums = data[col]?.mapNotNull { (it as? Number)?.toDouble() } ?: emptyList()
                if (nums.isNotEmpty()) res[col] = round4(nums.sum())
            }
            return res
        }

        fun toHtml(): String {
            val sb = StringBuilder()
            sb.append("<table class=\"dataframe\" style=\"border-collapse: collapse; width: 100%; font-family: monospace; font-size: 13px;\">\n")
            sb.append("  <thead>\n")
            sb.append("    <tr style=\"background-color: #27272a; color: #f43f5e; text-align: left;\">\n")
            sb.append("      <th style=\"padding: 8px; border: 1px solid #3f3f46;\"></th>\n")
            for (col in columns) {
                sb.append("      <th style=\"padding: 8px; border: 1px solid #3f3f46; color: #38bdf8;\">")
                sb.append(escapeHtml(col))
                sb.append("</th>\n")
            }
            sb.append("    </tr>\n")
            sb.append("  </thead>\n")
            sb.append("  <tbody>\n")

            for (r in 0 until rowCount) {
                val bg = if (r % 2 == 0) "#18181b" else "#27272a"
                sb.append("    <tr style=\"background-color: $bg;\">\n")
                sb.append("      <th style=\"padding: 6px 10px; border: 1px solid #3f3f46; color: #94a3b8;\">")
                sb.append(resolvedIndex.getOrNull(r) ?: r)
                sb.append("</th>\n")
                for (col in columns) {
                    val cellVal = data[col]?.getOrNull(r)?.toString() ?: ""
                    sb.append("      <td style=\"padding: 6px 10px; border: 1px solid #3f3f46; color: #f4f4f5;\">")
                    sb.append(escapeHtml(cellVal))
                    sb.append("</td>\n")
                }
                sb.append("    </tr>\n")
            }

            sb.append("  </tbody>\n")
            sb.append("</table>\n")
            sb.append("<div style=\"color: #71717a; font-size: 11px; margin-top: 4px;\">${rowCount} rows × ${columns.size} columns</div>")
            return sb.toString()
        }

        override fun toString(): String {
            val sb = StringBuilder()
            val colWidths = mutableMapOf<String, Int>()
            colWidths[""] = resolvedIndex.maxOfOrNull { it.toString().length }?.coerceAtLeast(3) ?: 3
            for (col in columns) {
                val maxCell = data[col]?.maxOfOrNull { it?.toString()?.length ?: 4 } ?: 4
                colWidths[col] = maxOf(col.length, maxCell) + 2
            }

            // Header
            sb.append(String.format(java.util.Locale.US, "%-${colWidths[""]}s", ""))
            for (col in columns) {
                sb.append(String.format(java.util.Locale.US, "%${colWidths[col]}s", col))
            }
            sb.append("\n")

            // Rows
            val displayLimit = 10
            val displayRows = if (rowCount <= displayLimit) (0 until rowCount).toList() else (0 until 5).toList() + (rowCount - 5 until rowCount).toList()

            var dotted = false
            for (r in displayRows) {
                if (rowCount > displayLimit && r >= 5 && !dotted) {
                    sb.append("...\n")
                    dotted = true
                }
                val idxStr = resolvedIndex.getOrNull(r)?.toString() ?: r.toString()
                sb.append(String.format(java.util.Locale.US, "%-${colWidths[""]}s", idxStr))
                for (col in columns) {
                    val cellVal = data[col]?.getOrNull(r)?.toString() ?: "NaN"
                    sb.append(String.format(java.util.Locale.US, "%${colWidths[col]}s", cellVal))
                }
                sb.append("\n")
            }
            sb.append("\n[${rowCount} rows x ${columns.size} columns]")
            return sb.toString()
        }

        private fun round4(d: Double): Double = kotlin.math.round(d * 10000.0) / 10000.0

        private fun escapeHtml(text: String): String {
            return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
        }
    }

    fun DataFrame(data: Any?): DataFrame {
        return when (data) {
            is Map<*, *> -> {
                val colMap = mutableMapOf<String, List<Any?>>()
                for ((k, v) in data) {
                    val colName = k?.toString() ?: ""
                    val list = when (v) {
                        is List<*> -> v.map { it }
                        is DoubleArray -> v.map { it }
                        is IntArray -> v.map { it }
                        is NumpyModule.NDArray -> v.data.map { it }
                        else -> listOf(v)
                    }
                    colMap[colName] = list
                }
                PandasModule.DataFrame(data = colMap, index = null)
            }
            is List<*> -> {
                // List of dicts
                if (data.isNotEmpty() && data[0] is Map<*, *>) {
                    val allKeys = data.filterIsInstance<Map<*, *>>().flatMap { it.keys }.distinct().map { it.toString() }
                    val colMap = mutableMapOf<String, MutableList<Any?>>()
                    allKeys.forEach { colMap[it] = mutableListOf() }
                    for (row in data) {
                        val rowMap = row as? Map<*, *>
                        allKeys.forEach { k ->
                            colMap[k]?.add(rowMap?.get(k))
                        }
                    }
                    PandasModule.DataFrame(data = colMap, index = null)
                } else {
                    PandasModule.DataFrame(data = mapOf("0" to (data.map { it })), index = null)
                }
            }
            else -> PandasModule.DataFrame(data = emptyMap<String, List<Any?>>(), index = null)
        }
    }

    fun Series(data: Any?, name: String? = null): Series {
        val list = when (data) {
            is List<*> -> data.map { it }
            is DoubleArray -> data.map { it }
            is IntArray -> data.map { it }
            is NumpyModule.NDArray -> data.data.map { it }
            else -> emptyList()
        }
        return PandasModule.Series(name = name, values = list)
    }

    fun read_csv(csvText: String): DataFrame {
        val lines = csvText.trim().lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return DataFrame(emptyMap<String, List<Any?>>())

        val headers = parseCsvLine(lines[0])
        val cols = mutableMapOf<String, MutableList<Any?>>()
        headers.forEach { cols[it] = mutableListOf() }

        for (i in 1 until lines.size) {
            val vals = parseCsvLine(lines[i])
            headers.forEachIndexed { idx, h ->
                val raw = vals.getOrNull(idx)
                val parsed: Any? = raw?.toDoubleOrNull() ?: raw
                cols[h]?.add(parsed)
            }
        }
        return DataFrame(cols)
    }

    private fun parseCsvLine(line: String): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        for (c in line) {
            when {
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    tokens.add(sb.toString().trim())
                    sb.clear()
                }
                else -> sb.append(c)
            }
        }
        tokens.add(sb.toString().trim())
        return tokens
    }
}
