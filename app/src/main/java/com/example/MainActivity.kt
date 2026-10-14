package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.localjupyter.ui.screens.HomeScreen
import com.example.localjupyter.ui.screens.NotebookEditorScreen
import com.example.localjupyter.viewmodel.NotebookViewModel
import com.example.ui.theme.LocalJupyterTheme

enum class Screen {
    HOME,
    EDITOR
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LocalJupyterTheme {
                val notebookViewModel: NotebookViewModel = viewModel()
                var currentScreen by remember { mutableStateOf(Screen.HOME) }

                Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
                    when (screen) {
                        Screen.HOME -> {
                            HomeScreen(
                                viewModel = notebookViewModel,
                                onNavigateToEditor = { currentScreen = Screen.EDITOR },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Screen.EDITOR -> {
                            NotebookEditorScreen(
                                viewModel = notebookViewModel,
                                onNavigateBack = { currentScreen = Screen.HOME },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}
