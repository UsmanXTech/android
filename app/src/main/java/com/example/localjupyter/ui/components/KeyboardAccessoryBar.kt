package com.example.localjupyter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JupyterOrange
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceVariantDark

@Composable
fun KeyboardAccessoryBar(
    onInsertText: (String) -> Unit,
    onRunCurrentCell: () -> Unit,
    modifier: Modifier = Modifier
) {
    val quickTokens = listOf(
        "    " to "Tab",
        "()" to "( )",
        "[]" to "[ ]",
        "{}" to "{ }",
        ":" to ":",
        "=" to "=",
        "\"\"" to "\" \"",
        "''" to "' '",
        "+" to "+",
        "-" to "-",
        "*" to "*",
        "/" to "/",
        "**" to "**",
        "#" to "#",
        "," to ",",
        "." to ".",
        "_" to "_",
        "import " to "import",
        "def " to "def",
        "return " to "return",
        "print(" to "print()",
        "np." to "np.",
        "plt." to "plt.",
        "pd." to "pd."
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = SurfaceDark,
        tonalElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Prominent Run Button
            Button(
                onClick = onRunCurrentCell,
                colors = ButtonDefaults.buttonColors(containerColor = JupyterOrange),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .height(36.dp)
                    .testTag("keyboard_run_cell")
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Run Cell",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Run", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            // Quick Insertion Tokens
            quickTokens.forEach { (token, label) ->
                FilledTonalButton(
                    onClick = { onInsertText(token) },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = SurfaceVariantDark,
                        contentColor = Color(0xFFF4F4F5)
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("key_${label.replace(" ", "_")}")
                ) {
                    Text(
                        text = label,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
