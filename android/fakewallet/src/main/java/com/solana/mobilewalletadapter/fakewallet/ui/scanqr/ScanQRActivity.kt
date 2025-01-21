/*
 * Copyright (c) 2025 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.fakewallet.ui.scanqr

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.journeyapps.barcodescanner.BarcodeView
import com.solana.mobilewalletadapter.fakewallet.MobileWalletAdapterActivity
import com.solana.mobilewalletadapter.fakewallet.R
import com.solana.mobilewalletadapter.fakewallet.databinding.ActivityScanQrBinding
import com.solana.mobilewalletadapter.walletlib.association.RemoteAssociationUri

class ScanQRActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityScanQrBinding
    private lateinit var barcodeView: BarcodeView

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        viewBinding = ActivityScanQrBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        barcodeView = viewBinding.scannerViewFinder
    }

    override fun onResume() {
        super.onResume()
        barcodeView.resume()
        decodeQRSingle()
    }

    public override fun onPause() {
        super.onPause()
        barcodeView.pauseAndWait()
    }

    private fun decodeQRSingle() {
        barcodeView.decodeSingle { barcodeResult ->
            println("Barcode Result = ${barcodeResult.text}")
            runCatching {
                val remoteAssociationUri = RemoteAssociationUri(Uri.parse(barcodeResult.text))
                barcodeView.pause()

                startActivity(
                    Intent(applicationContext, MobileWalletAdapterActivity::class.java)
                        .setData(remoteAssociationUri.uri))
            }.getOrElse {
                Snackbar.make(viewBinding.root, R.string.str_invalid_barcode, Snackbar.LENGTH_LONG).apply {
                    setAction(R.string.label_try_again) { dismiss() }
                    addCallback(object : Snackbar.Callback() {
                        override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                            super.onDismissed(transientBottomBar, event)
                            decodeQRSingle()
                        }
                    })
                    show()
                }
            }
        }
    }
}