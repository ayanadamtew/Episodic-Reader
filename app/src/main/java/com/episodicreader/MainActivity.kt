package com.episodicreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.episodicreader.ui.EpisodicReaderRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appContainer = (application as EpisodicReaderApp).appContainer
        setContent {
            EpisodicReaderRoot(appContainer = appContainer)
        }
    }
}
