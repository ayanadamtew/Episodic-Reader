package com.episodicreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.episodicreader.ui.library.LibraryScreen
import com.episodicreader.ui.theme.EpisodicReaderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EpisodicReaderTheme {
                LibraryScreen()
            }
        }
    }
}

