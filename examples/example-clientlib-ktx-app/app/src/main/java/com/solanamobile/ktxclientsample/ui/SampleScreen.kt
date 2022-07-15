package com.solanamobile.ktxclientsample.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.solanamobile.ktxclientsample.viewmodel.SampleViewModel

@Composable
fun SampleScreen(
    viewmodel: SampleViewModel = hiltViewModel()
) {
    Column(
        modifier = Modifier.padding(8.dp)
    ) {
        Text(
            text = "Ktx Clientlib Sample",
            style = MaterialTheme.typography.h4
        )

        val memoText by remember { mutableStateOf(TextFieldValue("")) }

        OutlinedTextField(
            value = memoText,
            label = { Text("Memo Text") },
            onValueChange = { }
        )

        Button(onClick = { /*TODO*/ }) {
            Text("Record Memo")
        }

        Button(onClick = { /*TODO*/ }) {
            Text("Sign Message")
        }

        Button(onClick = { /*TODO*/ }) {
            Text("Request Airdrop")
        }
    }
}