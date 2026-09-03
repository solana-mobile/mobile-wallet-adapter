package com.solana.mobilewalletadapter.fakedapp.usecase

import android.util.Base64
import android.util.Log
import com.solana.mobilewalletadapter.common.ocms.OffchainMessage
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.io.ByteArrayInputStream

object OffChainMessageSigningUseCase {
    private val TAG = OffChainMessageSigningUseCase::class.java.simpleName

    fun verify(signedMessage: ByteArray, signature: ByteArray, publicKey: ByteArray, originalMessage: ByteArray) {
        if (!(signedMessage contentEquals originalMessage)) {
            Log.w(TAG, "Signed message differs from original message. Verifying provided signature on signed message.")
        }

        val publicKeyParams = Ed25519PublicKeyParameters(publicKey, 0)
        val signer = Ed25519Signer()
        signer.init(false, publicKeyParams)
        signer.update(signedMessage, 0, signedMessage.size)
        val verified = signer.verifySignature(signature)
        require(verified) { "Message signature is invalid" }
        Log.d(TAG, "Verified message signature with publicKey(base58)=${Base58EncodeUseCase(publicKey)}, "+
                "sig(base58)=${Base58EncodeUseCase(signature)}, " +
                "message(base64)=${Base64.encodeToString(signedMessage, Base64.NO_WRAP)}")
    }

    fun verifyOffChainEnvelope(signedOffchainMessage: ByteArray) {
        val signatureLength = 64;
        val signatureCount = signedOffchainMessage.first().toInt()
        val messageStart = 1 + signatureLength*signatureCount
        val signatures = Array(signatureCount) {
            signedOffchainMessage.copyOfRange(1 + signatureLength*it, 1 + signatureLength*(it + 1))
        }
        val signedMessage = signedOffchainMessage.copyOfRange(messageStart, signedOffchainMessage.size)
        val offchainMessage = OffchainMessage.deserialize(signedMessage)
        signatures.forEachIndexed { i, sig ->
            if (!sig.all { it == 0.toByte() }) {
                verify(signedMessage, sig, offchainMessage.requiredSigners[i], signedMessage)
            }
        }
    }
}