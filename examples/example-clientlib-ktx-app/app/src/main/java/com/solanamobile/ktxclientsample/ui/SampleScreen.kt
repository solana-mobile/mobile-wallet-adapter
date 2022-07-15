package com.solanamobile.ktxclientsample.ui

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import com.solanamobile.ktxclientsample.viewmodel.SampleViewModel

@Composable
fun SampleScreen(
    viewmodel: SampleViewModel = hiltViewModel()
) {
    Greeting(name = "Ktx Sample")
}

@Composable
fun Greeting(name: String) {
    Text(text = "Hello $name!")
}