package com.solanamobile.ktxclientsample.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
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

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Column {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colors.surface)
                    .padding(8.dp),
                text = "Ktx Client Sample",
                style = MaterialTheme.typography.h4,
                textAlign = TextAlign.Center
            )

            Divider(
                modifier = Modifier.padding(
                    top = 16.dp,
                    bottom = 16.dp
                )
            )

            var memoText by remember { mutableStateOf("") }

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = memoText,
                label = { Text("Memo Text") },
                onValueChange = { memoText = it }
            )

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = viewState.isConnected,
                onClick = { /*TODO*/ }
            ) {
                Text("Record Message")
            }
        }

//        Button(
//            modifier = Modifier.fillMaxWidth(),
//            onClick = {
//                if (!viewState.isConnected) {
//                    viewmodel.connectToWallet(intentSender)
//                } else {
//                    viewmodel.disconnect(intentSender)
//                }
//            }
//        ) {
//            Text(
//                text = if (!viewState.isConnected) "Connect to Wallet" else "Disconnect from Wallet"
//            )
//        }

//        Box(
//            modifier = Modifier
//                .background(
//                    if (viewState.isConnected) {
//                        Color.Green
//                    } else {
//                        Color.LightGray
//                    }
//                )
//                .fillMaxWidth()
//        ) {
//            Text(
//                modifier = Modifier
//                    .fillMaxWidth(),
//                textAlign = TextAlign.Center,
//                text = "Address: ${ viewState.userAddress }"
//            )
//        }

//        Button(
//            modifier = Modifier.fillMaxWidth(),
//            enabled = viewState.isConnected,
//            onClick = { /*TODO*/ }
//        ) {
//            Text("Sign Message")
//        }
//
//        Button(
//            modifier = Modifier.fillMaxWidth(),
//            enabled = viewState.isConnected,
//            onClick = { /*TODO*/ }
//        ) {
//            Text("Request Airdrop")
//        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(MaterialTheme.colors.surface)
                .padding(8.dp)
        ) {
            Row {
                Text(
                    text = "Balance: \u25ce 5",
                    style = MaterialTheme.typography.h5
                )

                Spacer(Modifier.weight(1f))
                
                Button(
                    onClick = { /*TODO*/ }
                ) {
                    Text(text = "Add Funds")
                }
            }

            Row {
                Icon(
                    imageVector = Icons.Filled.VpnKey,
                    contentDescription = "Add Address",
                    tint = Color.Black,
                    modifier = Modifier
                        .size(24.dp)
                )

                Text(
                    text = "8hEeWszgrA2XkRK4GH6zL4Qq5wJBwotwsB6VEweD8YEQ"
                )
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { /*TODO*/ }
            ) {
                Text(
                    text = "Add funds to enable"
                )
            }
        }
    }
}