package com.solana.mobilewalletadapter.fakedapp.usecase

import android.util.Base64
import android.util.Log
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

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
}