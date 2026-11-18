package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.ExifLensScreen
import com.example.ui.ExifLensViewModel
import com.example.ui.theme.ExifLensTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: ExifLensViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val darkTheme = uiState.darkThemeOverride ?: isSystemInDarkTheme()

            ExifLensTheme(darkTheme = darkTheme) {
                ExifLensScreen(
                    viewModel = viewModel,
                    isDarkTheme = darkTheme
                )
            }
        }
    }
}
