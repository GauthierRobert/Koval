package com.koval.trainingplanner

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.koval.trainingplanner.ui.navigation.KovalNavHost
import com.koval.trainingplanner.ui.theme.Background
import com.koval.trainingplanner.ui.theme.KovalTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KovalTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Background,
                ) {
                    KovalNavHost(intent = intent)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}
