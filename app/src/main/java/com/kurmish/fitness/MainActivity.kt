package com.kurmish.fitness

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kurmish.fitness.ui.HomeScreen
import com.kurmish.fitness.ui.theme.KurmishFitnessTheme

/** Единственная Activity приложения: хост для Compose-экранов. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KurmishFitnessTheme {
                HomeScreen()
            }
        }
    }
}
