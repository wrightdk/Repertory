package io.github.wrightdk.repertory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.wrightdk.repertory.navigation.RepertoryNavHost
import io.github.wrightdk.repertory.ui.theme.RepertoryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RepertoryTheme {
                RepertoryNavHost()
            }
        }
    }
}
