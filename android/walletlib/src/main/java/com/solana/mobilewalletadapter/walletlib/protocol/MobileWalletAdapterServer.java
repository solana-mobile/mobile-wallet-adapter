/*
 * Copyright (c) 2022 Solana Labs, Inc.
 */

package com.solana.mobilewalletadapter.walletlib.protocol;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.ArraySet;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.solana.mobilewalletadapter.common.ProtocolContract;
import com.solana.mobilewalletadapter.common.protocol.PrivilegedMethod;
import com.solana.mobilewalletadapter.common.util.NotifyOnCompleteFuture;
import com.solana.mobilewalletadapter.common.util.NotifyingCompletableFuture;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public class MobileWalletAdapterServer extends JsonRpc20Server {
    private static final String TAG = MobileWalletAdapterServer.class.getSimpleName();

    @NonNull
    private final Handler mHandler;
    @NonNull
    private final MethodHandlers mMethodHandlers;

    public MobileWalletAdapterServer(@NonNull Looper ioLooper, @NonNull MethodHandlers methodHandlers) {
        mHandler = new Handler(ioLooper);
        mMethodHandlers = methodHandlers;
    }

    @Override
    protected void dispatchRpc(@Nullable Object id,
                               @NonNull String method,
                               @Nullable Object params) {
        try {
            if (ProtocolContract.METHOD_AUTHORIZE.equals(method)) {
                handleAuthorize(id, params);
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed sending response for id=" + id, e);
        }
    }

    private void handleAuthorize(@Nullable Object id, @Nullable Object params) throws IOException {
        if (!(params instanceof JSONObject)) {
            handleRpcError(id, ERROR_INVALID_PARAMS, "params must be either a JSONObject", null);
            return;
        }

        final JSONObject o = (JSONObject) params;

        final JSONObject ident = o.optJSONObject(ProtocolContract.PARAMETER_IDENTITY);
        final Uri identityUri;
        final Uri iconUri;
        final String identityName;
        if (ident != null) {
            identityUri = ident.has(ProtocolContract.PARAMETER_IDENTITY_URI) ?
                    Uri.parse(ident.optString(ProtocolContract.PARAMETER_IDENTITY_URI)) : null;
            if (identityUri != null && (!identityUri.isAbsolute() || !identityUri.isHierarchical())) {
                handleRpcError(id, ERROR_INVALID_PARAMS, "When specified, identity.uri must be an absolute, hierarchical URI", null);
                return;
            }
            iconUri = ident.has(ProtocolContract.PARAMETER_IDENTITY_ICON) ?
                    Uri.parse(ident.optString(ProtocolContract.PARAMETER_IDENTITY_ICON)) : null;
            if (iconUri != null && !iconUri.isRelative()) {
                handleRpcError(id, ERROR_INVALID_PARAMS, "When specified, identity.icon must be a relative URI", null);
                return;
            }
            identityName = ident.has(ProtocolContract.PARAMETER_IDENTITY_NAME) ?
                    ident.optString(ProtocolContract.PARAMETER_IDENTITY_NAME) : null;
            if (identityName != null && identityName.isEmpty()) {
                handleRpcError(id, ERROR_INVALID_PARAMS, "When specified, identity.name must be a non-empty string", null);
                return;
            }
        } else {
            identityUri = null;
            iconUri = null;
            identityName = null;
        }

        final JSONArray pm = o.optJSONArray(ProtocolContract.PARAMETER_PRIVILEGED_METHODS);
        final int numPrivilegedMethods = (pm != null) ? pm.length() : 0;
        if (numPrivilegedMethods == 0) {
            handleRpcError(id, ERROR_INVALID_PARAMS, "privileged_methods must be a non-empty array of method names", null);
            return;
        }
        final ArraySet<PrivilegedMethod> privilegedMethods = new ArraySet<>(numPrivilegedMethods);
        for (int i = 0; i < numPrivilegedMethods; i++) {
            final String methodName = pm.optString(i);
            final PrivilegedMethod method = PrivilegedMethod.fromMethodName(methodName);
            if (method == null) {
                handleRpcError(id, ERROR_INVALID_PARAMS, "privileged_methods contains unknown method name '" + methodName + "'", null);
                return;
            }
        }

        final AuthorizeRequest request = new AuthorizeRequest(mHandler, id, identityUri, iconUri, identityName, privilegedMethods);
        request.notifyOnComplete(this::onAuthorizeComplete);
        mMethodHandlers.authorize(request);
    }

    private void onAuthorizeComplete(@NonNull NotifyOnCompleteFuture<AuthorizeResult> future) {
        final AuthorizeRequest request = (AuthorizeRequest) future;

        try {
            final AuthorizeResult result;
            try {
                result = request.get();
            } catch (ExecutionException e) {
                handleRpcError(request.id, ERROR_INTERNAL, "Error while processing authorize request", null);
                return;
            } catch (InterruptedException e) {
                throw new RuntimeException("Should never occur!");
            }

            if (result == null) {
                handleRpcError(request.id, ERROR_INTERNAL, "Error while processing authorize request", null);
                return;
            } else if (!result.authorized) {
                handleRpcError(request.id, ProtocolContract.ERROR_AUTHORIZATION_FAILED, "Authorization declined", null);
                return;
            }

            final JSONObject o = new JSONObject();
            try {
                o.put(ProtocolContract.RESULT_AUTH_TOKEN, "42"); // TODO: generate real auth token
                o.put(ProtocolContract.RESULT_PUBLIC_KEY, "4242424242"); // TODO: real public key
                o.put(ProtocolContract.RESULT_WALLET_URI_BASE, result.walletUriBase); // OK if null
            } catch (JSONException e) {
                Log.e(TAG, "Failed preparing authorize response", e);
                return;
            }

            handleRpcResult(request.id, o);
        } catch (IOException e) {
            Log.e(TAG, "Failed sending response for id=" + request.id, e);
        }
    }

    private static abstract class RequestFuture<T> extends NotifyingCompletableFuture<T> {
        @Nullable
        public final Object id;

        public RequestFuture(@NonNull Handler handler,
                             @Nullable Object id) {
            super(handler);
            this.id = id;
        }
    }

    public static class AuthorizeRequest extends RequestFuture<AuthorizeResult> {
        @Nullable
        public final Uri identityUri;
        @Nullable
        public final Uri iconUri;
        @Nullable
        public final String identityName;
        @NonNull
        public final Set<PrivilegedMethod> privilegedMethods;

        private AuthorizeRequest(@NonNull Handler handler,
                                 @Nullable Object id,
                                 @Nullable Uri identityUri,
                                 @Nullable Uri iconUri,
                                 @Nullable String identityName,
                                 @NonNull Set<PrivilegedMethod> privilegedMethods) {
            super(handler, id);
            this.identityUri = identityUri;
            this.iconUri = iconUri;
            this.identityName = identityName;
            this.privilegedMethods = privilegedMethods;
        }
    }

    public static class AuthorizeResult {
        public final boolean authorized;
        @Nullable
        public final Uri walletUriBase;

        public AuthorizeResult(boolean authorized, @Nullable Uri walletUriBase) {
            this.authorized = authorized;
            this.walletUriBase = walletUriBase;
        }
    }

    public interface MethodHandlers {
        void authorize(@NonNull AuthorizeRequest request);
    }
}
