/*
 * Copyright (c) 2022 Solana Labs, Inc.
 */

package com.solana.mobilewalletadapter.walletlib.protocol;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.ArraySet;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Size;

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

    public interface MethodHandlers {
        void authorize(@NonNull AuthorizeRequest request);
        void signTransaction(@NonNull SignTransactionRequest request);
    }

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
            } else if (ProtocolContract.METHOD_SIGN_TRANSACTION.equals(method)) {
                handleSignTransaction(id, params);
            } else {
                handleRpcError(id, JsonRpc20Server.ERROR_METHOD_NOT_FOUND, "method '" + method +
                        "' not available", null);
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed sending response for id=" + id, e);
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

    // =============================================================================================
    // authorize
    // =============================================================================================

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
                final Throwable cause = e.getCause();
                if (cause instanceof RequestDeclinedException) {
                    handleRpcError(request.id, ProtocolContract.ERROR_AUTHORIZATION_FAILED, "authorize request declined", null);
                } else {
                    handleRpcError(request.id, ERROR_INTERNAL, "Error while processing authorize request", null);
                }
                return;
            } catch (InterruptedException e) {
                throw new RuntimeException("Should never occur!");
            }

            assert(result != null); // checked in AuthorizeRequest.complete()

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

        @Override
        public boolean complete(@Nullable AuthorizeResult result) {
            if (result == null) {
                throw new IllegalArgumentException("A non-null result must be provided");
            }
            return super.complete(result);
        }

        public boolean completeWithDecline() {
            return completeExceptionally(new RequestDeclinedException("authorize request declined"));
        }
    }

    public static class AuthorizeResult {
        @Nullable
        public final Uri walletUriBase;

        public AuthorizeResult(@Nullable Uri walletUriBase) {
            this.walletUriBase = walletUriBase;
        }
    }

    // =============================================================================================
    // sign_transaction
    // =============================================================================================

    private void handleSignTransaction(@Nullable Object id, @Nullable Object params) throws IOException {
        if (!(params instanceof JSONObject)) {
            handleRpcError(id, ERROR_INVALID_PARAMS, "params must be either a JSONObject", null);
            return;
        }

        final JSONObject o = (JSONObject) params;

        final JSONArray transactionsArray = o.optJSONArray(ProtocolContract.PARAMETER_PAYLOADS);
        if (transactionsArray == null) {
            handleRpcError(id, ERROR_INVALID_PARAMS, "request must contain an array of transaction paylods", null);
            return;
        }
        final int numTransactions = transactionsArray.length();
        if (numTransactions == 0) {
            handleRpcError(id, ERROR_INVALID_PARAMS, "request must contain at least one transaction payload", null);
            return;
        }

        final byte[][] transactions = new byte[numTransactions][];
        for (int i = 0; i < numTransactions; i++) {
            final String transaction = transactionsArray.optString(i);
            if (transaction.isEmpty()) {
                handleRpcError(id, ERROR_INVALID_PARAMS, "transactions cannot be empty", null);
                return;
            }
            transactions[i] = Base64.decode(transaction, Base64.URL_SAFE);
        }

        @SuppressLint("Range")
        final SignTransactionRequest request = new SignTransactionRequest(mHandler, id, transactions);
        request.notifyOnComplete(this::onSignTransactionComplete);
        mMethodHandlers.signTransaction(request);
    }

    private void onSignTransactionComplete(@NonNull NotifyOnCompleteFuture<SignTransactionResult> future) {
        final SignTransactionRequest request = (SignTransactionRequest) future;

        try {
            final SignTransactionResult result;
            try {
                result = request.get();
            } catch (ExecutionException e) {
                final Throwable cause = e.getCause();
                if (cause instanceof RequestDeclinedException) {
                    handleRpcError(request.id, ProtocolContract.ERROR_NOT_SIGNED, "sign_transaction request declined", null);
                } else if (cause instanceof ReauthorizationRequiredException) {
                    handleRpcError(request.id, ProtocolContract.ERROR_REAUTHORIZE, "auth_token requires reauthorization", null);
                } else if (cause instanceof AuthTokenNotValidException) {
                    handleRpcError(request.id, ProtocolContract.ERROR_AUTHORIZATION_FAILED, "auth_token not valid for sign_transaction", null);
                } else if (cause instanceof InvalidPayloadException) {
                    handleRpcError(request.id, ProtocolContract.ERROR_INVALID_PAYLOAD, "payload invalid for sign_transaction",
                            createInvalidPayloadData(((InvalidPayloadException) cause).valid));
                } else {
                    handleRpcError(request.id, ERROR_INTERNAL, "Error while processing sign_transaction request", null);
                }
                return;
            } catch (InterruptedException e) {
                throw new RuntimeException("Should never occur!");
            }

            assert(result != null); // checked in SignTransactionRequest.complete()
            assert(result.signedTransactions.length == request.transactions.length); // checked in SignTransactionRequest.complete()

            final JSONObject o = new JSONObject();
            try {
                final JSONArray signedTransactions = new JSONArray();
                for (byte[] st : result.signedTransactions) {
                    final String stb64 = Base64.encodeToString(st, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
                    signedTransactions.put(stb64);
                }
                o.put(ProtocolContract.RESULT_SIGNED_PAYLOADS, signedTransactions);
            } catch (JSONException e) {
                Log.e(TAG, "Failed preparing sign_transaction response", e);
                return;
            }

            handleRpcResult(request.id, o);
        } catch (IOException e) {
            Log.e(TAG, "Failed sending response for id=" + request.id, e);
        }
    }

    @NonNull
    private String createInvalidPayloadData(@NonNull @Size(min = 1) boolean[] valid) {
        final JSONArray arr = new JSONArray();
        for (boolean v : valid) {
            arr.put(v);
        }

        final JSONObject o = new JSONObject();
        try {
            o.put(ProtocolContract.DATA_INVALID_PAYLOAD_VALID, arr);
        } catch (JSONException e) {
            throw new RuntimeException("Failed constructing invalid payload data", e);
        }

        return o.toString();
    }

    public static class SignTransactionRequest extends RequestFuture<SignTransactionResult> {
        @NonNull
        @Size(min = 1)
        public final byte[][] transactions;

        private SignTransactionRequest(@NonNull Handler handler,
                                 @Nullable Object id,
                                 @NonNull @Size(min = 1) byte[][] transactions) {
            super(handler, id);
            this.transactions = transactions;
        }

        @Override
        public boolean complete(@Nullable SignTransactionResult result) {
            if (result == null) {
                throw new IllegalArgumentException("A non-null result must be provided");
            } else if (result.signedTransactions.length != transactions.length) {
                throw new IllegalArgumentException("Number of signed transactions does not match the number of requested signatures");
            }

            return super.complete(result);
        }

        public boolean completeWithDecline() {
            return completeExceptionally(new RequestDeclinedException("sign_transaction request declined"));
        }

        public boolean completeWithReauthorizationRequired() {
            return completeExceptionally(new ReauthorizationRequiredException("auth_token requires reauthorization"));
        }

        public boolean completeWithAuthTokenNotValid() {
            return completeExceptionally(new AuthTokenNotValidException("auth_token not valid for sign_transaction"));
        }

        public boolean completeWithInvalidTransactions(@NonNull @Size(min = 1) boolean[] valid) {
            if (valid.length != transactions.length) {
                throw new IllegalArgumentException("Number of valid transaction entries does not match the number of requested signatures");
            }
            return completeExceptionally(new InvalidPayloadException("One or more invalid transactions provided to sign_transaction", valid));
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

    // =============================================================================================
    // Common exceptions
    // =============================================================================================

    public static abstract class MobileWalletAdapterServerException extends Exception {
        MobileWalletAdapterServerException(@NonNull String m) { super(m); }
    }

    public static class RequestDeclinedException extends MobileWalletAdapterServerException {
        RequestDeclinedException(@NonNull String m) { super(m); }
    }

    public static class ReauthorizationRequiredException extends MobileWalletAdapterServerException {
        public ReauthorizationRequiredException(@NonNull String m) { super(m); }
    }

    public static class AuthTokenNotValidException extends MobileWalletAdapterServerException {
        public AuthTokenNotValidException(@NonNull String m) { super (m); }
    }

    public static class InvalidPayloadException extends MobileWalletAdapterServerException {
        @NonNull
        @Size(min = 1)
        public final boolean[] valid;

        public InvalidPayloadException(@NonNull String m, @NonNull @Size(min = 1) boolean[] valid) {
            super(m);
            this.valid = valid;
        }
    }
}
