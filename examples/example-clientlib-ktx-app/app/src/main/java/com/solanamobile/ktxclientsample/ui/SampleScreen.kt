package com.solanamobile.ktxclientsample.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solanamobile.ktxclientsample.viewmodel.SampleViewModel

@Composable
fun SampleScreen(
    intentSender: ActivityResultSender,
    viewmodel: SampleViewModel = hiltViewModel()
) {
    val viewState = viewmodel.viewState.collectAsState().value

    Column(
        modifier = Modifier.padding(8.dp)
    ) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = "Ktx Client Sample",
            style = MaterialTheme.typography.h4
        )

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                viewmodel.connectToWallet(intentSender)
            }
        ) {
            Text("Connect to Wallet")
        }

        Divider(
            modifier = Modifier.padding(
                top = 16.dp,
                bottom = 16.dp
            )
        )

        val memoText by remember { mutableStateOf(TextFieldValue("")) }

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = memoText,
            label = { Text("Memo Text") },
            onValueChange = { }
        )

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = viewState.isConnected,
            onClick = { /*TODO*/ }
        ) {
            Text("Record Message")
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = viewState.isConnected,
            onClick = { /*TODO*/ }
        ) {
            Text("Sign Message")
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = viewState.isConnected,
            onClick = { /*TODO*/ }
        ) {
            Text("Request Airdrop")
        }
    }
}