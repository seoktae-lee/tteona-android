package com.seoktaedev.tteona

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.seoktaedev.tteona.features.root.AppRoot
import com.seoktaedev.tteona.ui.theme.TteonaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            TteonaTheme {
                AppRoot()
            }
        }
    }
}
