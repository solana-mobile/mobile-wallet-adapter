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

            Column(
                modifier = Modifier.padding(8.dp)
            ) {
                Divider(
                    modifier = Modifier.padding(
                        top = 16.dp,
                        bottom = 16.dp
                    )
                )

                var memoText by remember { mutableStateOf("") }

                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    value = memoText,
                    label = { Text("Memo Text") },
                    onValueChange = { memoText = it }
                )

                val openDialog = remember { mutableStateOf(false)  }

                if (openDialog.value) {
                    AlertDialog(
                        onDismissRequest = {
                            openDialog.value = false
                        },
                        text = {
                            Text("Clicking the \"Publish\" button will send a transaction that publishes the text you've typed above onto the Solana Blockchain using the Memo program.")
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    openDialog.value = false
                                }
                            ) {
                                Text("Got it")
                            }
                        },
                    )
                }

                Row {
                    Button(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        enabled = viewState.canTransact && memoText.isNotEmpty(),
                        onClick = { /*TODO*/ }
                    ) {
                        Text("Publish Memo")
                    }

                    OutlinedButton(
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = MaterialTheme.colors.secondaryVariant
                        ),
                        onClick = {
                            openDialog.value = true
                        }
                    ) {
                        Text(
                            text = "?",
                            color = MaterialTheme.colors.primary
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(MaterialTheme.colors.surface)
                .padding(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Balance: \u25ce",
                    style = MaterialTheme.typography.h5,
                )

                Text(
                    text = if (viewState.canTransact && viewState.solBalance >= 0) viewState.solBalance.toString() else "-",
                    style = MaterialTheme.typography.h5,
                )

                Spacer(Modifier.weight(1f))
                
                Button(
                    elevation = ButtonDefaults.elevation(defaultElevation = 4.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colors.secondaryVariant
                    ),
                    onClick = { /*TODO*/ }
                ) {
                    Text(
                        text = "Add Funds",
                        color = MaterialTheme.colors.primary
                    )
                }
            }

            Row {
                Icon(
                    imageVector = Icons.Filled.VpnKey,
                    contentDescription = "Add Address",
                    tint = Color.Black,
                    modifier = Modifier
                        .size(24.dp)
                        .padding(end = 8.dp)
                )

                Text(
                    text = if (viewState.canTransact) viewState.userAddress else "",
                    maxLines = 1
                )
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = false,
                onClick = { /*TODO*/ }
            ) {
                Text(
                    text = "Add funds to get started"
                )
            }
        }
    }
}