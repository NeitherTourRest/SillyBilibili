package com.example.sillybilibili

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.example.sillybilibili.ui.navigation.AppNavHost
import com.example.sillybilibili.ui.theme.SillyBilibiliTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SillyBilibiliTheme {
                val navController = rememberNavController()
                AppNavHost(navController = navController)
            }
        }
    }
}
