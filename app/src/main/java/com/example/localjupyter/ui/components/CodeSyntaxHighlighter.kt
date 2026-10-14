package com.example.localjupyter.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.ui.theme.*

object CodeSyntaxHighlighter {

    private val KEYWORDS = setOf(
        "import", "from", "as", "def", "class", "return", "for", "in", "while",
        "if", "elif", "else", "try", "except", "finally", "with", "lambda",
        "yield", "pass", "break", "continue", "raise", "assert", "global",
        "and", "or", "not", "is"
    )

    private val BUILTINS = setOf(
        "print", "len", "range", "sum", "min", "max", "abs", "round", "type",
        "str", "int", "float", "bool", "list", "dict", "set", "tuple",
        "enumerate", "zip", "map", "filter", "sorted", "reversed", "any", "all",
        "isinstance", "issubclass", "id", "repr", "dir", "help", "input",
        "True", "False", "None"
    )

    fun highlight(code: String): AnnotatedString {
        return buildAnnotatedString {
            append(code)
            var i = 0
            val len = code.length

            while (i < len) {
                val c = code[i]

                // Comments: # to end of line
                if (c == '#') {
                    val end = code.indexOf('\n', i).let { if (it == -1) len else it }
                    addStyle(
                        SpanStyle(color = SyntaxComment, fontFamily = FontFamily.Monospace),
                        i,
                        end
                    )
                    i = end
                    continue
                }

                // Strings: "..." or '...'
                if (c == '"' || c == '\'') {
                    val quote = c
                    var j = i + 1
                    var escaped = false
                    while (j < len) {
                        if (code[j] == '\\') {
                            escaped = !escaped
                        } else if (code[j] == quote && !escaped) {
                            j++
                            break
                        } else {
                            escaped = false
                        }
                        j++
                    }
                    addStyle(
                        SpanStyle(color = SyntaxString, fontFamily = FontFamily.Monospace),
                        i,
                        j.coerceAtMost(len)
                    )
                    i = j
                    continue
                }

                // Numbers
                if (c.isDigit() && (i == 0 || !code[i - 1].isLetterOrDigit() && code[i - 1] != '_')) {
                    var j = i
                    while (j < len && (code[j].isDigit() || code[j] == '.' || code[j] == 'e' || code[j] == 'E')) {
                        j++
                    }
                    addStyle(
                        SpanStyle(color = SyntaxNumber, fontFamily = FontFamily.Monospace),
                        i,
                        j
                    )
                    i = j
                    continue
                }

                // Identifiers (keywords, builtins, etc.)
                if (c.isLetter() || c == '_') {
                    var j = i
                    while (j < len && (code[j].isLetterOrDigit() || code[j] == '_')) {
                        j++
                    }
                    val word = code.substring(i, j)
                    when {
                        KEYWORDS.contains(word) -> {
                            addStyle(
                                SpanStyle(
                                    color = SyntaxKeyword,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.Monospace
                                ),
                                i,
                                j
                            )
                        }
                        BUILTINS.contains(word) -> {
                            addStyle(
                                SpanStyle(
                                    color = SyntaxBuiltin,
                                    fontFamily = FontFamily.Monospace
                                ),
                                i,
                                j
                            )
                        }
                        word == "np" || word == "plt" || word == "pd" -> {
                            addStyle(
                                SpanStyle(
                                    color = JupyterOrange,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = FontFamily.Monospace
                                ),
                                i,
                                j
                            )
                        }
                    }
                    i = j
                    continue
                }

                i++
            }
        }
    }
}
