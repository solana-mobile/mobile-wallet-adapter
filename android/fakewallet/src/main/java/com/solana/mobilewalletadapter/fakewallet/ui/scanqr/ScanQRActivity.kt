/*
 * Copyright (c) 2025 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.fakewallet.ui.scanqr

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.journeyapps.barcodescanner.BarcodeView
import com.solana.mobilewalletadapter.fakewallet.MobileWalletAdapterActivity
import com.solana.mobilewalletadapter.fakewallet.R
import com.solana.mobilewalletadapter.fakewallet.databinding.ActivityScanQrBinding
import com.solana.mobilewalletadapter.walletlib.association.RemoteAssociationUri

class ScanQRActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityScanQrBinding
    private lateinit var barcodeView: BarcodeView

    private val CAMERA_PERMISSION_CODE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE)
        }

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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, we can access the camera
                barcodeView.resume()
                decodeQRSingle()
            } else {
                // Permission denied, notify the user
                val userBlocked = !ActivityCompat.shouldShowRequestPermissionRationale(this@ScanQRActivity, Manifest.permission.CAMERA)
                Snackbar.make(viewBinding.root, R.string.str_camera_permission_required, Snackbar.LENGTH_LONG).apply {
                    setAction(if (userBlocked) R.string.label_open_settings else R.string.label_try_again) { dismiss() }
                    addCallback(object : Snackbar.Callback() {
                        override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                            super.onDismissed(transientBottomBar, event)
                            if (userBlocked) {
                                // User selected "Don't ask again" – direct them to settings
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                intent.data = Uri.fromParts("package", packageName, null)
                                startActivity(intent)
                            } else {
                                ActivityCompat.requestPermissions(this@ScanQRActivity, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE)
                            }
                        }
                    })
                    show()
                }
            }
        }
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