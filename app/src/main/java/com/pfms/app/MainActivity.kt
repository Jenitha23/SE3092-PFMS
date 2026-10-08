package com.pfms.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.pfms.app.ui.navigation.PfmsNavGraph
import com.pfms.app.ui.theme.PFMSTheme
import dagger.hilt.android.AndroidEntryPoint

// @AndroidEntryPoint is required for hiltViewModel(); FragmentActivity (a ComponentActivity
// subclass) is required by BiometricPrompt.
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PFMSTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PfmsNavGraph()
                }
            }
        }
    }
}