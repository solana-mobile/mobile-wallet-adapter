/*
 * Copyright (c) 2022 Solana Labs, Inc.
 */

package com.solana.mobilewalletadapter.clientlib.protocol;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Looper;
import android.util.Base64;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Size;

import com.solana.mobilewalletadapter.common.ProtocolContract;
import com.solana.mobilewalletadapter.common.protocol.PrivilegedMethod;
import com.solana.mobilewalletadapter.common.util.NotifyOnCompleteFuture;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class MobileWalletAdapterClient extends JsonRpc20Client {
    private static final int TIMEOUT_MS = 90000;

    public MobileWalletAdapterClient(@NonNull Looper mainLooper) {
        super(mainLooper);
    }

    public static RuntimeException unpackExecutionException(@NonNull ExecutionException e)
            throws JsonRpc20Exception, TimeoutException, CancellationException {
        final Throwable cause = e.getCause();
        if (cause instanceof JsonRpc20Exception) {
            throw (JsonRpc20Exception) cause;
        } else if (cause instanceof TimeoutException) {
            throw (TimeoutException) cause;
        } else if (cause instanceof CancellationException) {
            throw (CancellationException) cause;
        }
        return new RuntimeException("Unknown exception while waiting for a JSON-RPC 2.0 response", cause);
    }

    private static abstract class JsonRpc20MethodResultFuture<T> implements Future<T> {
        @NonNull
        protected final NotifyOnCompleteFuture<Object> mMethodCallFuture;

        private JsonRpc20MethodResultFuture(@NonNull NotifyOnCompleteFuture<Object> methodCallFuture) {
            mMethodCallFuture = methodCallFuture;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return mMethodCallFuture.cancel(mayInterruptIfRunning);
        }

        @Override
        public boolean isCancelled() {
            return mMethodCallFuture.isCancelled();
        }

        @Override
        public boolean isDone() {
            return mMethodCallFuture.isDone();
        }

        @Override
        public T get() throws ExecutionException, InterruptedException {
            final Object o;
            try {
                o = mMethodCallFuture.get();
            } catch (ExecutionException e) {
                final Throwable cause = e.getCause();
                if (cause instanceof JsonRpc20RemoteException) {
                    final JsonRpc20Exception mapped = processRemoteException((JsonRpc20RemoteException) cause);
                    if (mapped != null) {
                        throw new ExecutionException(e.getMessage(), mapped);
                    }
                }
                throw e;
            }
            try {
                return processResult(o);
            } catch (JsonRpc20InvalidResponseException e) {
                throw new ExecutionException(e);
            }
        }

        @Override
        public T get(long timeout, TimeUnit unit)
                throws ExecutionException, InterruptedException, TimeoutException {
            final Object o;
            try {
                o = mMethodCallFuture.get(timeout, unit);
            } catch (ExecutionException e) {
                final Throwable cause = e.getCause();
                if (cause instanceof JsonRpc20RemoteException) {
                    final JsonRpc20Exception mapped = processRemoteException((JsonRpc20RemoteException) cause);
                    if (mapped != null) {
                        throw new ExecutionException(e.getMessage(), mapped);
                    }
                }
                throw e;
            }
            try {
                return processResult(o);
            } catch (JsonRpc20InvalidResponseException e) {
                throw new ExecutionException(e);
            }
        }

        @NonNull
        protected abstract T processResult(@Nullable Object o)
                throws JsonRpc20InvalidResponseException;

        @Nullable
        protected JsonRpc20Exception processRemoteException(@NonNull JsonRpc20RemoteException e) {
            return null;
        }
    }

    // =============================================================================================
    // authorize
    // =============================================================================================

    @NonNull
    public AuthorizeFuture authorizeAsync(@Nullable Uri identityUri,
                                          @Nullable Uri iconUri,
                                          @Nullable String identityName,
                                          @NonNull Set<PrivilegedMethod> privilegedMethods)
            throws IOException {
        if (identityUri != null && (!identityUri.isAbsolute() || !identityUri.isHierarchical())) {
            throw new IllegalArgumentException("If non-null, identityUri must be an absolute, hierarchical Uri");
        } else if (iconUri != null && !iconUri.isRelative()) {
            throw new IllegalArgumentException("If non-null, iconRelativeUri must be a relative Uri");
        }

        final JSONObject authorize;
        try {
            final JSONObject identity = new JSONObject();
            identity.put(ProtocolContract.PARAMETER_IDENTITY_URI, identityUri);
            identity.put(ProtocolContract.PARAMETER_IDENTITY_ICON, iconUri);
            identity.put(ProtocolContract.PARAMETER_IDENTITY_NAME, identityName);
            final JSONArray privMethods = new JSONArray();
            for (PrivilegedMethod pm : privilegedMethods) {
                privMethods.put(pm.methodName);
            }
            authorize = new JSONObject();
            authorize.put(ProtocolContract.PARAMETER_IDENTITY, identity);
            authorize.put(ProtocolContract.PARAMETER_PRIVILEGED_METHODS, privMethods);
        } catch (JSONException e) {
            throw new UnsupportedOperationException("Failed to create authorize JSON params", e);
        }

        return new AuthorizeFuture(methodCall(ProtocolContract.METHOD_AUTHORIZE, authorize, TIMEOUT_MS));
    }

    @NonNull
    public AuthorizeResult authorize(@Nullable Uri identityUri,
                                     @Nullable Uri iconUri,
                                     @Nullable String identityName,
                                     @NonNull Set<PrivilegedMethod> privilegedMethods)
            throws IOException, JsonRpc20Exception, TimeoutException, CancellationException {
        final AuthorizeFuture future = authorizeAsync(identityUri, iconUri, identityName, privilegedMethods);
        try {
            return future.get();
        } catch (ExecutionException e) {
            throw unpackExecutionException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while waiting for authorize response", e);
        }
    }

    public static class AuthorizeResult {
        @NonNull
        public final String authToken;
        @NonNull
        public final String publicKey;
        @Nullable
        public final Uri walletUriBase;

        public AuthorizeResult(@NonNull String authToken,
                               @NonNull String publicKey,
                               @Nullable Uri walletUriBase) {
            this.authToken = authToken;
            this.publicKey = publicKey;
            this.walletUriBase = walletUriBase;
        }
    }

    public static class AuthorizeFuture
            extends JsonRpc20MethodResultFuture<AuthorizeResult>
            implements NotifyOnCompleteFuture<AuthorizeResult> {
        private AuthorizeFuture(@NonNull NotifyOnCompleteFuture<Object> methodCallFuture) {
            super(methodCallFuture);
        }

        @NonNull
        @Override
        protected AuthorizeResult processResult(@Nullable Object o)
                throws JsonRpc20InvalidResponseException {
            if (!(o instanceof JSONObject)) {
                throw new JsonRpc20InvalidResponseException("expected result to be a JSON object");
            }

            final JSONObject jo = (JSONObject) o;

            final String authToken;
            try {
                authToken = jo.getString(ProtocolContract.RESULT_AUTH_TOKEN);
            } catch (JSONException e) {
                throw new JsonRpc20InvalidResponseException("expected an auth_token");
            }

            final String publicKey;
            try {
                publicKey = jo.getString(ProtocolContract.RESULT_PUBLIC_KEY);
            } catch (JSONException e) {
                throw new JsonRpc20InvalidResponseException("expected a public key");
            }

            final String walletUriBaseStr = jo.has(ProtocolContract.RESULT_WALLET_URI_BASE) ?
                    jo.optString(ProtocolContract.RESULT_WALLET_URI_BASE) : null;

            return new AuthorizeResult(authToken, publicKey,
                    (walletUriBaseStr != null) ? Uri.parse(walletUriBaseStr) : null);
        }

        @Override
        public void notifyOnComplete(@NonNull OnCompleteCallback<? super NotifyOnCompleteFuture<AuthorizeResult>> cb) {
            mMethodCallFuture.notifyOnComplete((f) -> cb.onComplete(this));
        }
    }

    // =============================================================================================
    // sign_transaction
    // =============================================================================================

    @NonNull
    public SignTransactionFuture signTransactionAsync(@NonNull String authToken,
                                                      @NonNull @Size(min = 1) byte[][] transactions)
            throws IOException {
        if (authToken.isEmpty()) {
            throw new IllegalArgumentException("authToken cannot be empty");
        }
        for (byte[] t : transactions) {
            if (t == null || t.length == 0) {
                throw new IllegalArgumentException("transactions must not contain null or empty transactions");
            }
        }

        final JSONObject signTransaction;
        try {
            final JSONArray payloads = new JSONArray();
            for (byte[] t : transactions) {
                final String tb64 = Base64
                        .encodeToString(t, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
                payloads.put(tb64);
            }

            signTransaction = new JSONObject();
            signTransaction.put(ProtocolContract.PARAMETER_AUTH_TOKEN, authToken);
            signTransaction.put(ProtocolContract.PARAMETER_PAYLOADS, payloads);
        } catch (JSONException e) {
            throw new UnsupportedOperationException("Failed to create sign_transaction JSON params", e);
        }

        return new SignTransactionFuture(
                methodCall(ProtocolContract.METHOD_SIGN_TRANSACTION, signTransaction, TIMEOUT_MS),
                transactions.length);
    }

    @NonNull
    public SignTransactionResult signTransaction(@NonNull String authToken,
                                                 @NonNull @Size(min = 1) byte[][] transactions)
            throws IOException, JsonRpc20Exception, TimeoutException, CancellationException {
        final SignTransactionFuture future = signTransactionAsync(authToken, transactions);
        try {
            return future.get();
        } catch (ExecutionException e) {
            throw unpackExecutionException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while waiting for authorize response", e);
        }
    }

    public static class SignTransactionResult {
        @NonNull
        @Size(min = 1)
        public final byte[][] signedTransactions;

        public SignTransactionResult(@NonNull @Size(min = 1) byte[][] signedTransactions) {
            this.signedTransactions = signedTransactions;
        }
    }

    public static class SignTransactionFuture
            extends JsonRpc20MethodResultFuture<SignTransactionResult>
            implements NotifyOnCompleteFuture<SignTransactionResult> {
        @IntRange(from = 1)
        private final int mExpectedNumSignedPayloads;

        private SignTransactionFuture(@NonNull NotifyOnCompleteFuture<Object> methodCallFuture,
                                      @IntRange(from = 1) int expectedNumSignedPayloads) {
            super(methodCallFuture);
            mExpectedNumSignedPayloads = expectedNumSignedPayloads;
        }

        @NonNull
        @Override
        protected SignTransactionResult processResult(@Nullable Object o)
                throws JsonRpc20InvalidResponseException {
            if (!(o instanceof JSONObject)) {
                throw new JsonRpc20InvalidResponseException("expected result to be a JSON object");
            }

            final JSONObject jo = (JSONObject) o;

            final JSONArray signed;
            try {
                signed = jo.getJSONArray(ProtocolContract.RESULT_SIGNED_PAYLOADS);
            } catch (JSONException e) {
                throw new JsonRpc20InvalidResponseException("Response expected to contain an array of signed payloads");
            }

            final int numSignedPayloads = signed.length();
            if (numSignedPayloads != mExpectedNumSignedPayloads) {
                throw new JsonRpc20InvalidResponseException("Response expected to contain " +
                        mExpectedNumSignedPayloads + " signed transactions; actual=" +
                        numSignedPayloads);
            }

            final byte[][] signedPayloads = new byte[numSignedPayloads][];
            for (int i = 0; i < numSignedPayloads; i++) {
                final String str;
                try {
                    str = signed.getString(i);
                } catch (JSONException e) {
                    throw new JsonRpc20InvalidResponseException("Response signed payloads must be Strings");
                }
                signedPayloads[i] = Base64.decode(str, Base64.URL_SAFE);
            }

            @SuppressLint("Range")
            final SignTransactionResult result = new SignTransactionResult(signedPayloads);
            return result;
        }

        @Nullable
        @Override
        protected JsonRpc20Exception processRemoteException(@NonNull JsonRpc20RemoteException remoteException) {
            if (remoteException.code != ProtocolContract.ERROR_INVALID_PAYLOAD) {
                return null;
            }
            try {
                return new InvalidPayloadException(remoteException.getMessage(),
                        remoteException.data, mExpectedNumSignedPayloads);
            } catch (JsonRpc20InvalidResponseException e) {
                return e;
            }
        }

        @Override
        public void notifyOnComplete(@NonNull OnCompleteCallback<? super NotifyOnCompleteFuture<SignTransactionResult>> cb) {
            mMethodCallFuture.notifyOnComplete((f) -> cb.onComplete(this));
        }
    }

    // =============================================================================================
    // Common exceptions
    // =============================================================================================

    public static class InvalidPayloadException extends JsonRpc20RemoteException {
        @NonNull
        @Size(min = 1)
        public final boolean[] validTransactions;

        private InvalidPayloadException(@NonNull String message,
                                        @Nullable String data,
                                        @IntRange(from = 1) int expectedNumSignedPayloads)
                throws JsonRpc20InvalidResponseException {
            super(ProtocolContract.ERROR_INVALID_PAYLOAD, message, data);
            this.validTransactions = processData(data, expectedNumSignedPayloads);
        }

        @NonNull
        @Size(min = 1)
        private static boolean[] processData(@Nullable String data,
                                             @IntRange(from = 1) int expectedNumSignedPayloads)
                throws JsonRpc20InvalidResponseException {
            if (data == null) {
                throw new JsonRpc20InvalidResponseException("data should not be null");
            }

            final JSONArray arr;
            try {
                final JSONObject o = new JSONObject(data);
                arr = o.getJSONArray(ProtocolContract.DATA_INVALID_PAYLOAD_VALID);
            } catch (JSONException e) {
                throw new JsonRpc20InvalidResponseException("data is not a valid ERROR_INVALID_PAYLOAD result");
            }
            final int numValid = arr.length();
            if (numValid != expectedNumSignedPayloads) {
                throw new JsonRpc20InvalidResponseException("valid should contain " +
                        expectedNumSignedPayloads + " entries; actual=" + numValid);
            }

            final boolean[] valid = new boolean[numValid];
            for (int i = 0; i < numValid; i++) {
                try {
                    valid[i] = arr.getBoolean(i);
                } catch (JSONException e) {
                    throw new JsonRpc20InvalidResponseException("valid entries must be Booleans");
                }
            }

            return valid;
        }
    }
}
