/*
 * Copyright (c) 2022 Solana Labs, Inc.
 */

package com.solana.mobilewalletadapter.clientlib.protocol;

import android.net.Uri;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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

    private static RuntimeException unpackExecutionException(@NonNull ExecutionException e)
            throws JsonRpc20Exception, TimeoutException, CancellationException {
        final Throwable cause = e.getCause();
        if (cause instanceof JsonRpc20Exception) {
            throw (JsonRpc20Exception) cause;
        } else if (cause instanceof TimeoutException) {
            throw (TimeoutException) cause;
        } else if (cause instanceof CancellationException) {
            throw (CancellationException) cause;
        }
        return new RuntimeException("Failed during sync wait for a JSON-RPC 2.0 response", cause);
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
            final Object o = mMethodCallFuture.get();
            try {
                return processResult(o);
            } catch (JsonRpc20InvalidResponseException e) {
                throw new ExecutionException(e);
            }
        }

        @Override
        public T get(long timeout, TimeUnit unit)
                throws ExecutionException, InterruptedException, TimeoutException {
            final Object o = mMethodCallFuture.get(timeout, unit);
            try {
                return processResult(o);
            } catch (JsonRpc20InvalidResponseException e) {
                throw new ExecutionException(e);
            }
        }

        protected abstract T processResult(@Nullable Object o)
                throws JsonRpc20InvalidResponseException;
    }

    public static class AuthorizeResult {
        @NonNull
        public final String authToken;
        @NonNull
        public final String publicKey;
        @Nullable
        public final Uri walletUriBase;

        public AuthorizeResult(@NonNull String authToken, @NonNull String publicKey, @Nullable Uri walletUriBase) {
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

        @Override
        protected AuthorizeResult processResult(@Nullable Object o) throws JsonRpc20InvalidResponseException {
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
}
