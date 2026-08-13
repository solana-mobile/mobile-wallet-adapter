/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.fakewallet

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.funkatronics.encoders.Base58
import com.solana.mobilewalletadapter.fakewallet.data.Seed
import com.solana.mobilewalletadapter.fakewallet.databinding.ActivityMainBinding
import com.solana.mobilewalletadapter.fakewallet.ui.scanqr.ScanQRActivity
import com.solana.mobilewalletadapter.fakewallet.usecase.Bip39UseCase
import com.solana.mobilewalletadapter.fakewallet.usecase.Ed25519Slip10UseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters

class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding
    private val keyRepository get() = (application as FakeWalletApplication).keyRepository
    private var deriveAddressJob: Job? = null
    private var activeAddress: String? = null
    private var currentMnemonic: String? = null
    private var mnemonicRevealed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        // Handle layout insets to avoid overlapping top and bottom system bars on Android 15+
        ViewCompat.setOnApplyWindowInsetsListener(viewBinding.root) { v, windowInsets ->
            val insets =
                windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            // Apply the insets as a margin to the view
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
                topMargin = insets.top
                rightMargin = insets.right
                bottomMargin = insets.bottom
            }

            // Return CONSUMED so the window insets don't keep passing down to descendant views
            WindowInsetsCompat.CONSUMED
        }

        viewBinding.buttonStartRemote.setOnClickListener {
            startActivity(Intent(applicationContext, ScanQRActivity::class.java))
        }

        viewBinding.textActiveAccount.setOnClickListener {
            activeAddress?.let { address ->
                copyToClipboard("address", address, sensitive = false, R.string.str_address_copied)
            }
        }

        viewBinding.textMnemonic.setOnClickListener {
            val mnemonic = currentMnemonic ?: return@setOnClickListener
            mnemonicRevealed = !mnemonicRevealed
            if (mnemonicRevealed) {
                viewBinding.textMnemonic.text = mnemonic
                copyToClipboard("seed phrase", mnemonic, sensitive = true, R.string.str_seed_phrase_copied)
            } else {
                viewBinding.textMnemonic.setText(R.string.str_seed_phrase_hidden)
            }
        }

        viewBinding.buttonGenerateSeed.setOnClickListener {
            lifecycleScope.launch { keyRepository.setSeed(Bip39UseCase.generatePhrase()) }
        }

        viewBinding.buttonImportSeed.setOnClickListener {
            showImportSeedDialog()
        }

        viewBinding.buttonClearSeed.setOnClickListener {
            AlertDialog.Builder(this)
                .setMessage(R.string.str_clear_seed_confirm)
                .setPositiveButton(R.string.label_clear_seed) { _, _ ->
                    lifecycleScope.launch { keyRepository.clearSeed() }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        viewBinding.buttonAccountPrev.setOnClickListener { adjustAccountIndex(-1) }
        viewBinding.buttonAccountNext.setOnClickListener { adjustAccountIndex(1) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                keyRepository.seed.collect { seed -> bindSeed(seed) }
            }
        }
    }

    private fun bindSeed(seed: Seed?) {
        viewBinding.groupNoSeed.isVisible = seed == null
        viewBinding.groupSeed.isVisible = seed != null

        deriveAddressJob?.cancel()
        activeAddress = null
        if (seed?.mnemonic != currentMnemonic) {
            currentMnemonic = seed?.mnemonic
            mnemonicRevealed = false
        }

        if (seed == null) {
            val localProps = BuildConfig.PRIVATE_KEY
            if (localProps == null) {
                viewBinding.labelActiveAccount.setText(R.string.label_active_account)
                viewBinding.textActiveAccount.setText(R.string.str_active_account_ephemeral)
            } else {
                viewBinding.labelActiveAccount.setText(R.string.str_active_account_local_props)
                showActiveAddress {
                    MobileWalletAdapterViewModel.decodePrivateKey(localProps)
                }
            }
            return
        }

        if (mnemonicRevealed) {
            viewBinding.textMnemonic.text = seed.mnemonic
        } else {
            viewBinding.textMnemonic.setText(R.string.str_seed_phrase_hidden)
        }
        viewBinding.textAccountIndex.text = seed.accountIndex.toString()
        viewBinding.buttonAccountPrev.isEnabled = seed.accountIndex > 0

        viewBinding.labelActiveAccount.text = getString(
            R.string.label_active_account_path, Ed25519Slip10UseCase.derivationPath(seed.accountIndex))
        showActiveAddress {
            Ed25519Slip10UseCase.derivePrivateKey(Bip39UseCase.toSeed(seed.mnemonic), seed.accountIndex)
        }
    }

    private fun copyToClipboard(label: String, text: String, sensitive: Boolean, toastResId: Int) {
        val clip = ClipData.newPlainText(label, text)
        if (sensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // keeps the Android 13+ clipboard preview overlay from displaying the content
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
        // Android 13+ shows its own clipboard confirmation UI
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            Toast.makeText(this, toastResId, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showActiveAddress(derivePrivateKey: () -> ByteArray) {
        viewBinding.textActiveAccount.text = "…"
        deriveAddressJob = lifecycleScope.launch {
            val address = withContext(Dispatchers.Default) {
                Base58.encodeToString(
                    Ed25519PrivateKeyParameters(derivePrivateKey(), 0).generatePublicKey().encoded)
            }
            activeAddress = address
            viewBinding.textActiveAccount.text = address
        }
    }

    private fun adjustAccountIndex(delta: Int) {
        lifecycleScope.launch {
            val seed = keyRepository.getSeed() ?: return@launch
            keyRepository.setAccountIndex((seed.accountIndex + delta).coerceAtLeast(0))
        }
    }

    private fun showImportSeedDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.str_import_seed_hint)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.label_import_seed)
            .setView(input)
            .setPositiveButton(R.string.label_import_seed) { _, _ ->
                val phrase = Bip39UseCase.normalize(input.text.toString())
                try {
                    Bip39UseCase.validate(phrase)
                } catch (e: IllegalArgumentException) {
                    Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                lifecycleScope.launch { keyRepository.setSeed(phrase) }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
