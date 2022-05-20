package com.solana.mobilewalletadapter.walletlib.scenario;

import android.net.Uri;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.solana.mobilewalletadapter.common.protocol.PrivilegedMethod;
import com.solana.mobilewalletadapter.walletlib.authorization.AuthRecord;
import com.solana.mobilewalletadapter.walletlib.authorization.AuthRepository;
import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterServer;

import java.util.Set;

public class AuthorizeRequest {
    private static final String TAG = AuthorizeRequest.class.getSimpleName();

    @NonNull
    private final Handler mIoHandler;

    @NonNull
    private final AuthRepository mAuthRepository;

    @NonNull
    private final MobileWalletAdapterServer.AuthorizeRequest mRequest;

    /*package*/ AuthorizeRequest(@NonNull Handler ioHandler,
                                 @NonNull AuthRepository authRepository,
                                 @NonNull MobileWalletAdapterServer.AuthorizeRequest request) {
        mIoHandler = ioHandler;
        mAuthRepository = authRepository;
        mRequest = request;
    }

    @Nullable
    public String getIdentityName() {
        return mRequest.identityName;
    }

    @Nullable
    public Uri getIdentityUri() {
        return mRequest.identityUri;
    }

    @Nullable
    public Uri getIconRelativeUri() {
        return mRequest.iconUri;
    }

    @NonNull
    public Set<PrivilegedMethod> getPrivilegedMethods() {
        return mRequest.privilegedMethods;
    }

    public void completeWithAuthorize(@NonNull String publicKey, @Nullable Uri walletUriBase) {
        mIoHandler.post(() -> completeWithAuthToken(publicKey, walletUriBase));
    }

    // Note: runs in IO thread context
    private void completeWithAuthToken(@NonNull String publicKey, @Nullable Uri walletUriBase) {
        final String name = mRequest.identityName != null ? mRequest.identityName : "";
        final Uri uri = mRequest.identityUri != null ? mRequest.identityUri : Uri.EMPTY;
        final Uri relativeIconUri = mRequest.iconUri != null ? mRequest.iconUri : Uri.EMPTY;
        final AuthRecord authRecord = mAuthRepository.issue(
                name, uri, relativeIconUri, publicKey, mRequest.privilegedMethods);
        Log.d(TAG, "Authorize request completed successfully; issued auth: " + authRecord);

        final String authToken = mAuthRepository.toAuthToken(authRecord);
        mRequest.complete(new MobileWalletAdapterServer.AuthorizeResult(
                authToken, publicKey, walletUriBase));
    }

    public void completeWithDecline() {
        mRequest.completeExceptionally(new MobileWalletAdapterServer.RequestDeclinedException(
                "authorize request declined"));
    }
}
