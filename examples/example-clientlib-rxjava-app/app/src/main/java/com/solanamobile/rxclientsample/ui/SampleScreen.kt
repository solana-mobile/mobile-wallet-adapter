package com.solanamobile.rxclientsample.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solanamobile.rxclientsample.viewmodel.SampleViewModel

@Composable
fun SampleScreen(
    intentSender: ActivityResultSender,
    viewModel: SampleViewModel = hiltViewModel()
) {
    val viewState = viewModel.viewState.collectAsState().value

    LaunchedEffect(
        key1 = Unit,
        block = {
            viewModel.loadConnection()
        }
    )

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
                        onClick = {
                            viewModel.publishMemo(intentSender, memoText)
                        }
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

                if (viewState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(8.dp)
                            .height(24.dp)
                            .width(24.dp)
                    )
                }

                Button(
                    elevation = ButtonDefaults.elevation(defaultElevation = 4.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colors.secondaryVariant
                    ),
                    onClick = {
                        viewModel.addFunds(intentSender)
                    }
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
                    contentDescription = "Address",
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
                enabled = viewState.canTransact,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color.Red.copy(red = 0.7f)
                ),
                onClick = {
                    viewModel.disconnect(intentSender)
                }
            ) {
                Text(
                    color = MaterialTheme.colors.onPrimary,
                    text = if (viewState.canTransact) "Disconnect" else "Add funds to get started"
                )
            }

            val uriHandler = LocalUriHandler.current

            if (viewState.memoTx.isNotEmpty()) {
                Snackbar(
                    modifier = Modifier.padding(8.dp)
                ) {
                    val sourceStr = "Memo Successfully Published: VIEW"
                    val annotatedString = buildAnnotatedString {
                        append(sourceStr)

                        addStyle(
                            style = SpanStyle(
                                color = Color.Yellow,
                                fontWeight = FontWeight.Bold
                            ),
                            start = 29,
                            end = 33
                        )
                        addStringAnnotation(
                            tag = "URL",
                            annotation = "https://explorer.solana.com/tx/${ viewState.memoTx }?cluster=devnet",
                            start = 29,
                            end = 33
                        )

                        addStyle(
                            style = SpanStyle(
                                color = Color.White
                            ),
                            start = 0,
                            end = 28
                        )
                    }

                    ClickableText(
                        text = annotatedString,
                        onClick = {
                            annotatedString.getStringAnnotations("URL", it, it)
                                .firstOrNull()?.let { strAnnotation ->
                                    uriHandler.openUri(strAnnotation.item)
                                }
                        }
                    )
                }
            }
        }
    }
}