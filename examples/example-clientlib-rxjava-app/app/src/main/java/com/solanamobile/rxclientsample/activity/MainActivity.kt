package com.solanamobile.rxclientsample.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.ui.Modifier
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solanamobile.rxclientsample.ui.SampleScreen
import com.solanamobile.rxclientsample.theme.KtxClientSampleTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity(), ActivityResultSender {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            KtxClientSampleTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    SampleScreen(this)
                }
            }
        }
    }

    override fun launch(intent: Intent) {
        startActivityForResult(intent, 0)
    }
}