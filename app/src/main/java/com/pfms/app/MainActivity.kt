package com.pfms.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pfms.app.ui.navigation.PfmsNavGraph
import com.pfms.app.ui.theme.PFMSTheme
import com.pfms.app.ui.theme.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint

// @AndroidEntryPoint is required for hiltViewModel(); FragmentActivity (a ComponentActivity
// subclass) is required by BiometricPrompt.
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val themeViewModel: ThemeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
            PFMSTheme(themeMode = themeMode) {
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