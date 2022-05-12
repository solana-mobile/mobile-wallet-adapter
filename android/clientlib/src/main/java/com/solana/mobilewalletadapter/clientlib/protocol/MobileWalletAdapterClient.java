/*
 * Copyright (c) 2022 Solana Labs, Inc.
 */

package com.solana.mobilewalletadapter.clientlib.protocol;

import android.net.Uri;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.solana.mobilewalletadapter.common.protocol.PrivilegedMethod;
import com.solana.mobilewalletadapter.common.util.NotifyOnCompletionFuture;

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
                                          @NonNull Set<PrivilegedMethod> privilegedMethods,
                                          @Nullable NotifyOnCompletionFuture.FutureCompletionNotifier<Object> onCompletion)
            throws IOException {
        if (identityUri != null && (!identityUri.isAbsolute() || !identityUri.isHierarchical())) {
            throw new IllegalArgumentException("If non-null, identityUri must be an absolute, hierarchical Uri");
        } else if (iconUri != null && !iconUri.isRelative()) {
            throw new IllegalArgumentException("If non-null, iconRelativeUri must be a relative Uri");
        }

        final JSONObject authorize;
        try {
            final JSONObject identity = new JSONObject();
            identity.put("uri", identityUri);
            identity.put("icon", iconUri);
            identity.put("name", identityName);
            final JSONArray privMethods = new JSONArray();
            for (PrivilegedMethod pm : privilegedMethods) {
                privMethods.put(pm.methodName);
            }
            authorize = new JSONObject();
            authorize.put("identity", identity);
            authorize.put("privileged_methods", privMethods);
        } catch (JSONException e) {
            throw new UnsupportedOperationException("Failed to create authorize JSON params", e);
        }

        return new AuthorizeFuture(methodCall("authorize", authorize, onCompletion, TIMEOUT_MS));
    }

    public AuthorizeResult authorize(@Nullable Uri identityUri,
                                     @Nullable Uri iconUri,
                                     @Nullable String identityName,
                                     @NonNull Set<PrivilegedMethod> privilegedMethods)
            throws IOException, JsonRpc20Exception, TimeoutException, CancellationException {
        final AuthorizeFuture future = authorizeAsync(identityUri, iconUri, identityName,
                privilegedMethods, null);
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

    private static abstract class JsonRpc20ClientResultFuture<T> implements Future<T> {
        @NonNull
        private final Future<Object> mJsonRpc20ClientFuture;

        private JsonRpc20ClientResultFuture(@NonNull Future<Object> jsonRpc20ClientFuture) {
            mJsonRpc20ClientFuture = jsonRpc20ClientFuture;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return mJsonRpc20ClientFuture.cancel(mayInterruptIfRunning);
        }

        @Override
        public boolean isCancelled() {
            return mJsonRpc20ClientFuture.isCancelled();
        }

        @Override
        public boolean isDone() {
            return mJsonRpc20ClientFuture.isDone();
        }

        @Override
        public T get() throws ExecutionException, InterruptedException {
            final Object o = mJsonRpc20ClientFuture.get();
            try {
                return processResult(o);
            } catch (JsonRpc20InvalidResponseException e) {
                throw new ExecutionException(e);
            }
        }

        @Override
        public T get(long timeout, TimeUnit unit)
                throws ExecutionException, InterruptedException, TimeoutException {
            final Object o = mJsonRpc20ClientFuture.get(timeout, unit);
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
        @Nullable
        public final Uri walletUriBase;

        public AuthorizeResult(@NonNull String authToken, @Nullable Uri walletUriBase) {
            this.authToken = authToken;
            this.walletUriBase = walletUriBase;
        }
    }

    public static class AuthorizeFuture
            extends JsonRpc20ClientResultFuture<AuthorizeResult> {
        private AuthorizeFuture(@NonNull Future<Object> jsonRpc20ClientFuture) {
            super(jsonRpc20ClientFuture);
        }

        @Override
        protected AuthorizeResult processResult(@Nullable Object o) throws JsonRpc20InvalidResponseException {
            if (!(o instanceof JSONObject)) {
                throw new JsonRpc20InvalidResponseException("expected result to be a JSON object");
            }

            final JSONObject jo = (JSONObject) o;

            final String authToken;
            try {
                authToken = jo.getString("auth_token");
            } catch (JSONException e) {
                throw new JsonRpc20InvalidResponseException("expected an auth_token");
            }

            final String walletUriBaseStr = jo.optString("wallet_uri_base");

            return new AuthorizeResult(authToken, Uri.parse(walletUriBaseStr));
        }
    }
}
