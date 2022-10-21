/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.fakewallet.usecase

import android.annotation.TargetApi
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import com.solana.digitalassetlinks.AndroidAppPackageVerifier
import com.solana.mobilewalletadapter.walletlib.association.AssociationUri
import com.solana.mobilewalletadapter.walletlib.association.LocalAssociationUri
import com.solana.mobilewalletadapter.walletlib.association.RemoteAssociationUri
import kotlinx.coroutines.*
import java.net.URI

@TargetApi(Build.VERSION_CODES.N) // for conditional use of PackageManager.getPackageUid(...)
class ClientTrustUseCase(private val repositoryScope: CoroutineScope,
                         private val packageManager: PackageManager,
                         private val callingPackage: String?,
                         associationUri: AssociationUri) {
    private val associationType: AssociationType

    init {
        associationType = when (associationUri) {
            is LocalAssociationUri -> {
                if (callingPackage != null) {
                    Log.d(TAG, "Creating client trust use case for a local app scenario")
                    AssociationType.LocalFromApp
                } else {
                    Log.d(TAG, "Creating client trust use case for a local browser scenario")
                    AssociationType.LocalFromBrowser
                }
            }
            is RemoteAssociationUri -> {
                Log.d(TAG, "Creating client trust use case for a remote scenario")
                AssociationType.Remote
            }
            else -> throw UnsupportedOperationException("Unrecognized association URI type")
        }
    }

    val verificationInProgress = VerificationInProgress(associationType.scopeTag)
    val verificationTimedOut = VerificationFailed(associationType.scopeTag)

    fun verifyAuthorizationSourceAsync(clientIdentityUri: Uri?): Deferred<VerificationState> {
        return when (associationType) {
            AssociationType.LocalFromBrowser -> {
                if (clientIdentityUri != null) {
                    repositoryScope.async(Dispatchers.IO) {
                        // TODO: kick off web-based client verification here
                        delay(1500) // fake this operation taking a little while
                        Log.d(TAG, "Web-scoped authorization verification not yet implemented")
                        VerificationSucceeded(
                            AssociationType.LocalFromBrowser.scopeTag,
                            clientIdentityUri.authority!! // absolute URIs always have an authority
                        )
                    }
                } else {
                    Log.d(TAG, "Client did not provide an identity URI; not verifiable")
                    CompletableDeferred(NotVerifiable(AssociationType.LocalFromBrowser.scopeTag))
                }
            }
            AssociationType.LocalFromApp -> {
                if (clientIdentityUri != null) {
                    repositoryScope.async(Dispatchers.IO) {
                        val verifier = AndroidAppPackageVerifier(packageManager)
                        val callingPackage = callingPackage!! // NOTE: AssociationType.LOCAL_FROM_APP implies that callingPackage is not null
                        val verified = try {
                            verifier.verify(callingPackage, URI.create(clientIdentityUri.toString()))
                        } catch (e: AndroidAppPackageVerifier.CouldNotVerifyPackageException) {
                            Log.w(TAG, "Package verification failed for callingPackage=$callingPackage, clientIdentityUri=$clientIdentityUri")
                            false
                        }
                        if (verified) {
                            val uid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                packageManager.getPackageUid(callingPackage, 0)
                            } else {
                                packageManager.getApplicationInfo(callingPackage, 0).uid
                            }
                            Log.d(TAG, "App-scoped authorization succeeded for '${callingPackage}'")
                            VerificationSucceeded(
                                AssociationType.LocalFromApp.scopeTag,
                                uid.toString()
                            )
                        } else {
                            Log.w(TAG, "App-scoped authorization failed for '${callingPackage}'")
                            VerificationFailed(AssociationType.LocalFromApp.scopeTag)
                        }
                    }
                } else {
                    Log.d(TAG, "Client did not provide an identity URI; not verifiable")
                    CompletableDeferred(NotVerifiable(AssociationType.LocalFromApp.scopeTag))
                }
            }
            AssociationType.Remote -> {
                Log.w(TAG, "Remote authorizations are not verifiable")
                CompletableDeferred(NotVerifiable(AssociationType.Remote.scopeTag))
            }
        }
    }

    fun verifyReauthorizationSourceAsync(
        authorizationScope: String,
        clientIdentityUri: Uri?
    ): Deferred<VerificationState> {
        return if (!authorizationScope.startsWith(associationType.scopeTag)) {
            Log.w(TAG, "Reauthorization failed; association type mismatch")
            CompletableDeferred(VerificationFailed(associationType.scopeTag))
        } else if (authorizationScope.length == associationType.scopeTag.length) {
            Log.d(TAG, "Unqualified authorization scopes are not verifiable")
            CompletableDeferred(NotVerifiable(associationType.scopeTag))
        } else {
            verifyAuthorizationSourceAsync(clientIdentityUri)
        }
    }

    // Note: the authorizationScope and clientIdentityUri parameters should be retrieved from a
    // trusted source (e.g. a local database), as they are used as part of request verification.
    // When used with requests originating from walletlib, these parameters will originate in
    // AuthRepository, which is backed by a local database.
    fun verifyPrivilegedMethodSource(
        authorizationScope: String,
        clientIdentityUri: Uri?
    ): Boolean {
        return if (authorizationScope.startsWith(AssociationType.LocalFromBrowser.scopeTag)) {
            if (associationType != AssociationType.LocalFromBrowser) {
                Log.w(TAG, "Attempt to use a web-scoped authorization with a non-web client")
                false
            } else if (authorizationScope.length == AssociationType.LocalFromBrowser.scopeTag.length) {
                Log.d(TAG, "Unqualified web-scoped authorization, continuing")
                true
            } else if (authorizationScope[AssociationType.LocalFromBrowser.scopeTag.length] != SCOPE_DELIMITER) {
                Log.w(TAG, "Unexpected character '${authorizationScope[AssociationType.LocalFromBrowser.scopeTag.length]}' in scope; expected '$SCOPE_DELIMITER'")
                false
            } else {
                Log.d(TAG, "Treating qualified web-scoped authorization as a bearer token, continuing")
                true
            }
        } else if (authorizationScope.startsWith(AssociationType.LocalFromApp.scopeTag)) {
            if (associationType != AssociationType.LocalFromApp) {
                Log.w(TAG, "Attempt to use an app-scoped authorization with a non-app client")
                false
            } else if (authorizationScope.length == AssociationType.LocalFromApp.scopeTag.length) {
                Log.d(TAG, "Unqualified app-scoped authorization, continuing")
                true
            } else if (authorizationScope[AssociationType.LocalFromApp.scopeTag.length] != SCOPE_DELIMITER) {
                Log.w(TAG, "Unexpected character '${authorizationScope[AssociationType.LocalFromApp.scopeTag.length]}' in scope; expected '$SCOPE_DELIMITER'")
                false
            } else {
                val scopeUid = try {
                    authorizationScope.substring(AssociationType.LocalFromApp.scopeTag.length + 1).toInt()
                } catch (e: NumberFormatException) {
                    Log.w(TAG, "App-scoped authorization has invalid UID")
                    return false
                }

                val callingUid = try {
                    packageManager.getPackageUid(callingPackage ?: String(), 0)
                } catch (e: PackageManager.NameNotFoundException) {
                    Log.w(TAG, "Calling package is invalid")
                    return false
                }

                if (scopeUid == callingUid) {
                    Log.d(TAG, "App-scoped authorization matches calling identity, continuing")
                    true
                } else {
                    Log.w(TAG, "App-scoped authorization does not match calling identity")
                    false
                }
            }
        } else if (authorizationScope == AssociationType.Remote.scopeTag) {
            if (associationType != AssociationType.Remote) {
                Log.w(TAG, "Attempt to use a remote-scoped authorization with a local client")
                false
            } else {
                Log.d(TAG, "Authorization with remote source, continuing")
                true
            }
        } else {
            Log.w(TAG, "Unknown authorization scope")
            false
        }
    }

    private enum class AssociationType(val scopeTag: String) {
        LocalFromBrowser("web"),
        LocalFromApp("app"),
        Remote("rem")
    }

    sealed class VerificationState(private val scopeTag: String, private val qualifier: String?) {
        val authorizationScope: String by lazy {
            if (qualifier == null) scopeTag else "${scopeTag}$SCOPE_DELIMITER${qualifier}"
        }
    }
    class VerificationInProgress internal constructor(scopeTag: String) : VerificationState(scopeTag, null)
    class VerificationSucceeded internal constructor(scopeTag: String, qualifier: String) : VerificationState(scopeTag, qualifier)
    class VerificationFailed internal constructor(scopeTag: String) : VerificationState(scopeTag, null)
    class NotVerifiable internal constructor(scopeTag: String) : VerificationState(scopeTag, null)

    private companion object {
        val TAG = ClientTrustUseCase::class.simpleName
        const val SCOPE_DELIMITER = ','
    }
}