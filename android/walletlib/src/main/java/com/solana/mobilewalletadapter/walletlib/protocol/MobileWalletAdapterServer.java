/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.protocol;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Size;

import com.solana.mobilewalletadapter.common.ProtocolContract;
import com.solana.mobilewalletadapter.common.util.JsonPack;
import com.solana.mobilewalletadapter.common.util.NotifyOnCompleteFuture;
import com.solana.mobilewalletadapter.common.util.NotifyingCompletableFuture;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

public class MobileWalletAdapterServer extends JsonRpc20Server {
    private static final String TAG = MobileWalletAdapterServer.class.getSimpleName();

    @NonNull
    private final MobileWalletAdapterConfig mConfig;
    @NonNull
    private final Handler mHandler;
    @NonNull
    private final MethodHandlers mMethodHandlers;

    public interface MethodHandlers {
        void authorize(@NonNull AuthorizeRequest request);
        void reauthorize(@NonNull ReauthorizeRequest request);
        void deauthorize(@NonNull DeauthorizeRequest request);
        void signTransactions(@NonNull SignTransactionsRequest request);
        void signMessages(@NonNull SignMessagesRequest request);
        void signAndSendTransactions(@NonNull SignAndSendTransactionsRequest request);
    }

    public MobileWalletAdapterServer(@NonNull MobileWalletAdapterConfig config,
                                     @NonNull Looper ioLooper,
                                     @NonNull MethodHandlers methodHandlers) {
        mConfig = config;
        mHandler = new Handler(ioLooper);
        mMethodHandlers = methodHandlers;
    }

    @Override
    protected void dispatchRpc(@Nullable Object id,
                               @NonNull String method,
                               @Nullable Object params) {
        try {
            switch (method) {
                case ProtocolContract.METHOD_AUTHORIZE:
                    handleAuthorize(id, params);
                    break;
                case ProtocolContract.METHOD_REAUTHORIZE:
                    handleReauthorize(id, params);
                    break;
                case ProtocolContract.METHOD_DEAUTHORIZE:
                    handleDeauthorize(id, params);
                    break;
                case ProtocolContract.METHOD_GET_CAPABILITIES:
                    handleGetCapabilities(id, params);
                    break;
                case ProtocolContract.METHOD_SIGN_TRANSACTIONS:
                    handleSignTransactions(id, params);
                    break;
                case ProtocolContract.METHOD_SIGN_MESSAGES:
                    handleSignMessages(id, params);
                    break;
                case ProtocolContract.METHOD_SIGN_AND_SEND_TRANSACTIONS:
                    if (mConfig.supportsSignAndSendTransactions) {
                        handleSignAndSendTransactions(id, params);
                    } else {
                        handleRpcError(id, JsonRpc20Server.ERROR_METHOD_NOT_FOUND, "method '" +
                                ProtocolContract.METHOD_SIGN_AND_SEND_TRANSACTIONS +
                                "' not available", null);
                    }
                    break;
                default:
                    handleRpcError(id, JsonRpc20Server.ERROR_METHOD_NOT_FOUND, "method '" +
                            method + "' not available", null);
                    break;
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed sending response for id=" + id, e);
        }
    }

    private static abstract class RequestFuture<T> extends NotifyingCompletableFuture<T> {
        @Nullable
        public final Object id;

        public RequestFuture(@Nullable Object id) {
            this.id = id;
        }
    }

    // =============================================================================================
    // authorize/reauthorize shared types and methods
    // =============================================================================================

    // NOTE: future may be either AuthorizeRequest or ReauthorizeRequest
    private void onAuthorizationComplete(@NonNull NotifyOnCompleteFuture<AuthorizationResult> future) {
        final AuthorizationRequest request = (AuthorizationRequest) future;

        try {
            final AuthorizationResult result;
            try {
                result = request.get();
            } catch (ExecutionException e) {
                final Throwable cause = e.getCause();
                if (cause instanceof AuthorizationNotValidException || // NOTE: AuthorizationNotValidException only expected for reauthorize requests
                        cause instanceof RequestDeclinedException
                ) {
                    assert(!(cause instanceof AuthorizationNotValidException) || request instanceof ReauthorizeRequest);
                    handleRpcError(request.id, ProtocolContract.ERROR_AUTHORIZATION_FAILED, "authorization request failed", null);
                } else if (cause instanceof ClusterNotSupportedException) { // NOTE: ClusterNotSupportedException only expected for authorize requests
                    assert(request instanceof AuthorizeRequest);
                    handleRpcError(request.id, ProtocolContract.ERROR_CLUSTER_NOT_SUPPORTED, "invalid or unsupported cluster for authorization request", null);
                } else {
                    handleRpcError(request.id, ERROR_INTERNAL, "Error while processing authorization request", null);
                }
                return;
            } catch (CancellationException e) {
                // Treat cancellation as a declined request
                handleRpcError(request.id, ProtocolContract.ERROR_AUTHORIZATION_FAILED, "authorization request declined", null);
                return;
            } catch (InterruptedException e) {
                throw new RuntimeException("Should never occur!");
            }

            assert(result != null); // checked in AuthorizeRequest.complete()

            final String publicKeyBase64 = Base64.encodeToString(result.publicKey, Base64.NO_WRAP);

            final JSONObject o = new JSONObject();
            try {
                o.put(ProtocolContract.RESULT_AUTH_TOKEN, result.authToken);
                final JSONObject account = new JSONObject();
                account.put(ProtocolContract.RESULT_ACCOUNTS_ADDRESS, publicKeyBase64);
                if (result.accountLabel != null) {
                    account.put(ProtocolContract.RESULT_ACCOUNTS_LABEL, result.accountLabel);
                }
                final JSONArray accounts = new JSONArray().put(account); // TODO(#44): support multiple accounts
                o.put(ProtocolContract.RESULT_ACCOUNTS, accounts);
                o.put(ProtocolContract.RESULT_WALLET_URI_BASE, result.walletUriBase); // OK if null
            } catch (JSONException e) {
                throw new RuntimeException("Failed preparing authorization response", e);
            }

            handleRpcResult(request.id, o);
        } catch (IOException e) {
            Log.e(TAG, "Failed sending response for id=" + request.id, e);
        }
    }

    public static abstract class AuthorizationRequest extends RequestFuture<AuthorizationResult> {
        @Nullable
        public final Uri identityUri;
        @Nullable
        public final Uri iconUri;
        @Nullable
        public final String identityName;

        private AuthorizationRequest(@Nullable Object id,
                                     @Nullable Uri identityUri,
                                     @Nullable Uri iconUri,
                                     @Nullable String identityName) {
            super(id);
            this.identityUri = identityUri;
            this.iconUri = iconUri;
            this.identityName = identityName;
        }

        @Override
        public boolean complete(@Nullable AuthorizationResult result) {
            if (result == null) {
                throw new IllegalArgumentException("A non-null result must be provided");
            }
            return super.complete(result);
        }

        @NonNull
        @Override
        public String toString() {
            return "AuthorizationRequest{" +
                    "id=" + id +
                    ", identityUri=" + identityUri +
                    ", iconUri=" + iconUri +
                    ", identityName='" + identityName + '\'' +
                    '/' + super.toString() +
                    '}';
        }
    }

    public static class AuthorizationResult {
        @NonNull
        public final String authToken;

        @NonNull
        public final byte[] publicKey;

        @Nullable
        public final String accountLabel;

        @Nullable
        public final Uri walletUriBase;

        public AuthorizationResult(@NonNull String authToken,
                                   @NonNull byte[] publicKey,
                                   @Nullable String accountLabel,
                                   @Nullable Uri walletUriBase) {
            this.authToken = authToken;
            this.publicKey = publicKey;
            this.accountLabel = accountLabel;
            this.walletUriBase = walletUriBase;
        }

        @NonNull
        @Override
        public String toString() {
            return "AuthorizeResult{" +
                    "authToken=<REDACTED>" +
                    ", publicKey=" + Arrays.toString(publicKey) +
                    ", accountLabel='" + accountLabel + '\'' +
                    ", walletUriBase=" + walletUriBase +
                    '}';
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

        final String cluster = o.optString(ProtocolContract.PARAMETER_CLUSTER, ProtocolContract.CLUSTER_MAINNET_BETA);

        final AuthorizeRequest request = new AuthorizeRequest(id, identityUri, iconUri, identityName, cluster);
        request.notifyOnComplete((f) -> mHandler.post(() -> onAuthorizationComplete(f)));
        mMethodHandlers.authorize(request);
    }

    public static class AuthorizeRequest extends AuthorizationRequest {
        @NonNull
        public final String cluster;

        private AuthorizeRequest(@Nullable Object id,
                                 @Nullable Uri identityUri,
                                 @Nullable Uri iconUri,
                                 @Nullable String identityName,
                                 @NonNull String cluster) {
            super(id, identityUri, iconUri, identityName);
            this.cluster = cluster;
        }

        @Override
        public boolean complete(@Nullable AuthorizationResult result) {
            if (result == null) {
                throw new IllegalArgumentException("A non-null result must be provided");
            }
            return super.complete(result);
        }

        @NonNull
        @Override
        public String toString() {
            return "AuthorizeRequest{" +
                    "cluster='" + cluster + '\'' +
                    '/' + super.toString() +
                    '}';
        }
    }

    // =============================================================================================
    // reauthorize
    // =============================================================================================

    private void handleReauthorize(@Nullable Object id, @Nullable Object params) throws IOException {
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

        final String authToken = o.optString(ProtocolContract.PARAMETER_AUTH_TOKEN);
        if (authToken.isEmpty()) {
            handleRpcError(id, ERROR_INVALID_PARAMS, "auth_token must be a non-empty string", null);
            return;
        }

        final ReauthorizeRequest request = new ReauthorizeRequest(id, identityUri, iconUri, identityName, authToken);
        request.notifyOnComplete((f) -> mHandler.post(() -> onAuthorizationComplete(f)));
        mMethodHandlers.reauthorize(request);
    }

    public static class ReauthorizeRequest extends AuthorizationRequest {
        @NonNull
        public final String authToken;

        private ReauthorizeRequest(@Nullable Object id,
                                   @Nullable Uri identityUri,
                                   @Nullable Uri iconUri,
                                   @Nullable String identityName,
                                   @NonNull String authToken) {
            super(id, identityUri, iconUri, identityName);
            this.authToken = authToken;
        }

        @NonNull
        @Override
        public String toString() {
            return "ReauthorizeRequest{" +
                    "authToken=<REDACTED>" +
                    '/' + super.toString() +
                    '}';
        }
    }

    // =============================================================================================
    // deauthorize
    // =============================================================================================

    private void handleDeauthorize(@Nullable Object id, @Nullable Object params) throws IOException {
        if (!(params instanceof JSONObject)) {
            handleRpcError(id, ERROR_INVALID_PARAMS, "params must be either a JSONObject", null);
            return;
        }

        final JSONObject o = (JSONObject) params;

        final String authToken = o.optString(ProtocolContract.PARAMETER_AUTH_TOKEN);
        if (authToken.isEmpty()) {
            handleRpcError(id, ERROR_INVALID_PARAMS, "auth_token must be a non-empty string", null);
            return;
        }

        final DeauthorizeRequest request = new DeauthorizeRequest(id, authToken);
        request.notifyOnComplete((f) -> mHandler.post(() -> onDeauthorizeComplete(f)));
        mMethodHandlers.deauthorize(request);
    }

    private void onDeauthorizeComplete(@NonNull NotifyOnCompleteFuture<Object> future) {
        final DeauthorizeRequest request = (DeauthorizeRequest) future;

        try {
            try {
                request.get();
            } catch (ExecutionException | CancellationException e) {
                handleRpcError(request.id, ERROR_INTERNAL, "Error while processing deauthorize request", null);
                return;
            } catch (InterruptedException e) {
                throw new RuntimeException("Should never occur!");
            }

            handleRpcResult(request.id, new JSONObject());
        } catch (IOException e) {
            Log.e(TAG, "Failed sending response for id=" + request.id, e);
        }
    }

    public static class DeauthorizeRequest extends RequestFuture<Object> {
        @NonNull
        public final String authToken;

        private DeauthorizeRequest(@Nullable Object id,
                                   @NonNull String authToken) {
            super(id);
            this.authToken = authToken;
        }

        @NonNull
        @Override
        public String toString() {
            return "DeauthorizeRequest{" +
                    "id=" + id +
                    ", authToken=<REDACTED>" +
                    '/' + super.toString() +
                    '}';
        }
    }

    // =============================================================================================
    // get_capabilities
    // =============================================================================================

    private void handleGetCapabilities(@Nullable Object id, @Nullable Object params) throws IOException {
        if (!(params instanceof JSONObject)) {
            handleRpcError(id, ERROR_INVALID_PARAMS, "params must be either a JSONObject", null);
            return;
        }

        final JSONObject o = (JSONObject) params;
        if (o.keys().hasNext()) {
            handleRpcError(id, ERROR_INVALID_PARAMS, "params expected to be empty", null);
        }

        final JSONObject result = new JSONObject();
        try {
            result.put(ProtocolContract.RESULT_SUPPORTS_CLONE_AUTHORIZATION, false);
            result.put(ProtocolContract.RESULT_SUPPORTS_SIGN_AND_SEND_TRANSACTIONS, mConfig.supportsSignAndSendTransactions);
            if (mConfig.maxTransactionsPerSigningRequest != 0) {
                result.put(ProtocolContract.RESULT_MAX_TRANSACTIONS_PER_REQUEST, mConfig.maxTransactionsPerSigningRequest);
            }
            if (mConfig.maxMessagesPerSigningRequest != 0) {
                result.put(ProtocolContract.RESULT_MAX_MESSAGES_PER_REQUEST, mConfig.maxMessagesPerSigningRequest);
            }
        } catch (JSONException e) {
            throw new RuntimeException("Failed preparing get_capabilities response", e);
        }
        handleRpcResult(id, result);
    }

    // =============================================================================================
    // sign_* common
    // =============================================================================================

    public static abstract class SignRequest<T extends SignResult> extends RequestFuture<T> {
        @NonNull
        @Size(min = 1)
        public final byte[][] payloads;

        private SignRequest(@Nullable Object id,
                            @NonNull @Size(min = 1) byte[][] payloads) {
            super(id);
            this.payloads = payloads;
        }

        @Override
        public boolean complete(@Nullable T result) {
            if (result == null) {
                throw new IllegalArgumentException("A non-null result must be provided");
            } else if (result.getNumResults() != payloads.length) {
                throw new IllegalArgumentException("Number of signed results does not match the number of requested signatures");
            }

            return super.complete(result);
        }

        @Override
        public boolean completeExceptionally(@NonNull Exception ex) {
            if (ex instanceof InvalidPayloadsException) {
                final InvalidPayloadsException ipe = (InvalidPayloadsException) ex;
                if (ipe.valid.length != payloads.length) {
                    throw new IllegalArgumentException("Number of valid payload entries does not match the number of payloads to sign");
                }
            }
            return super.completeExceptionally(ex);
        }

        @NonNull
        @Override
        public String toString() {
            return "SignRequest{" +
                    "id=" + id +
                    ", payloads=" + Arrays.toString(payloads) +
                    ", super=" + super.toString() +
                    '}';
        }
    }

    public interface SignResult {
        int getNumResults();
    }

    public static class SignedPayloadsResult implements SignResult {
        @NonNull
        @Size(min = 1)
        public final byte[][] signedPayloads;

        public SignedPayloadsResult(@NonNull @Size(min = 1) byte[][] signedPayloads) {
            this.signedPayloads = signedPayloads;
        }

        @Override
        public int getNumResults() {
            return signedPayloads.length;
        }

        @NonNull
        @Override
        public String toString() {
            return "SignedPayloadsResult{signedPayloads=" + Arrays.toString(signedPayloads) + '}';
        }
    }

    @NonNull
    @Size(min = 1)
    private static byte[][] unpackPayloadsArray(@NonNull JSONObject jo) {
        final JSONArray payloadsArray = jo.optJSONArray(ProtocolContract.PARAMETER_PAYLOADS);
        if (payloadsArray == null) {
            throw new IllegalArgumentException("request must contain an array of payloads to sign");
        }
        final int numPayloads = payloadsArray.length();
        if (numPayloads == 0) {
            throw new IllegalArgumentException("request must contain at least one payload to sign");
        }

        final byte[][] payloads;
        try {
            payloads = JsonPack.unpackBase64PayloadsArrayToByteArrays(payloadsArray, false);
        } catch (JSONException e) {
            throw new IllegalArgumentException("payloads must be an array of base64url-encoded Strings");
        }
        // throws uncaught IllegalArgumentException if payloadsArray contains nulls

        return payloads;
    }

    private void onSignPayloadsComplete(@NonNull NotifyOnCompleteFuture<SignedPayloadsResult> future) {
        final SignRequest<SignedPayloadsResult> request = (SignRequest<SignedPayloadsResult>) future;

        try {
            final SignedPayloadsResult result;
            try {
                result = request.get();
            } catch (ExecutionException e) {
                final Throwable cause = e.getCause();
                if (cause instanceof RequestDeclinedException) {
                    handleRpcError(request.id, ProtocolContract.ERROR_NOT_SIGNED, "sign request declined", null);
                } else if (cause instanceof AuthorizationNotValidException) {
                    handleRpcError(request.id, ProtocolContract.ERROR_AUTHORIZATION_FAILED, "auth_token not valid for signing", null);
                } else if (cause instanceof InvalidPayloadsException) {
                    handleRpcError(request.id, ProtocolContract.ERROR_INVALID_PAYLOADS, "payloads invalid for signing",
                            createInvalidPayloadsData(((InvalidPayloadsException) cause).valid));
                } else if (cause instanceof TooManyPayloadsException) {
                    handleRpcError(request.id, ProtocolContract.ERROR_TOO_MANY_PAYLOADS, "number of payloads provided for signing exceeds implementation limit", null);
                } else {
                    handleRpcError(request.id, ERROR_INTERNAL, "Error while processing sign request", null);
                }
                return;
            } catch (CancellationException e) {
                // Treat cancellation as a declined request
                handleRpcError(request.id, ProtocolContract.ERROR_NOT_SIGNED, "sign request declined", null);
                return;
            } catch (InterruptedException e) {
                throw new RuntimeException("Should never occur!");
            }

            assert(result != null); // checked in SignPayloadsRequest.complete()
            assert(result.signedPayloads.length == request.payloads.length); // checked in SignPayloadsRequest.complete()

            final JSONArray signedPayloads = JsonPack.packByteArraysToBase64PayloadsArray(result.signedPayloads);
            final JSONObject o = new JSONObject();
            try {
                o.put(ProtocolContract.RESULT_SIGNED_PAYLOADS, signedPayloads);
            } catch (JSONException e) {
                throw new RuntimeException("Failed preparing sign response", e);
            }

            handleRpcResult(request.id, o);
        } catch (IOException e) {
            Log.e(TAG, "Failed sending response for id=" + request.id, e);
        }
    }

    @NonNull
    private String createInvalidPayloadsData(@NonNull @Size(min = 1) boolean[] valid) {
        final JSONArray arr = JsonPack.packBooleans(valid);
        final JSONObject o = new JSONObject();
        try {
            o.put(ProtocolContract.DATA_INVALID_PAYLOADS_VALID, arr);
        } catch (JSONException e) {
            throw new RuntimeException("Failed constructing invalid payloads data", e);
        }

        return o.toString();
    }

    private boolean checkExceedsSigningLimits(@IntRange(from = 0) int numPayloads,
                                              @NonNull SigningType type) {
        final int limit;
        switch (type) {
            case Transaction:
                limit = mConfig.maxTransactionsPerSigningRequest;
                break;
            case Message:
                limit = mConfig.maxMessagesPerSigningRequest;
                break;
            default:
                throw new RuntimeException("Unexpected type: " + type);
        }
        return (limit != 0 && numPayloads > limit);
    }

    public enum SigningType { Transaction, Message }

    // =============================================================================================
    // sign_transactions
    // =============================================================================================

    private void handleSignTransactions(@Nullable Object id,
                                        @Nullable Object params)
            throws IOException {
        if (!(params instanceof JSONObject)) {
            handleRpcError(id, ERROR_INVALID_PARAMS, "params must be either a JSONObject", null);
            return;
        }

        final JSONObject o = (JSONObject) params;

        final byte[][] payloads;
        try {
            payloads = unpackPayloadsArray(o);
        } catch (IllegalArgumentException e) {
            handleRpcError(id, ERROR_INVALID_PARAMS, "request contains an invalid payloads entry", null);
            return;
        }

        if (checkExceedsSigningLimits(payloads.length, SigningType.Transaction)) {
            handleRpcError(id, ProtocolContract.ERROR_TOO_MANY_PAYLOADS, "number of payloads provided for signing exceeds implementation limit", null);
            return;
        }

        final SignTransactionsRequest request = new SignTransactionsRequest(id, payloads);
        request.notifyOnComplete((f) -> mHandler.post(() -> onSignPayloadsComplete(f)));
        mMethodHandlers.signTransactions(request);
    }

    public static class SignTransactionsRequest extends SignRequest<SignedPayloadsResult> {
        protected SignTransactionsRequest(@Nullable Object id,
                                          @NonNull byte[][] payloads) {
            super(id, payloads);
        }

        @NonNull
        @Override
        public String toString() {
            return "SignTransactionsRequest{" +
                    "super=" + super.toString() +
                    '}';
        }
    }

    // =============================================================================================
    // sign_messages
    // =============================================================================================

    private void handleSignMessages(@Nullable Object id,
                                    @Nullable Object params)
            throws IOException {
        if (!(params instanceof JSONObject)) {
            handleRpcError(id, ERROR_INVALID_PARAMS, "params must be either a JSONObject", null);
            return;
        }

        final JSONObject o = (JSONObject) params;

        final byte[][] payloads;
        try {
            payloads = unpackPayloadsArray(o);
        } catch (IllegalArgumentException e) {
            handleRpcError(id, ERROR_INVALID_PARAMS, "request contains an invalid payloads entry", null);
            return;
        }

        final JSONArray addressesArray = o.optJSONArray(ProtocolContract.PARAMETER_ADDRESSES);
        if (addressesArray == null) {
            throw new IllegalArgumentException("request must contain an array of addresses with which to sign messages");
        }
        final int numAddresses = addressesArray.length();
        if (numAddresses == 0) {
            throw new IllegalArgumentException("request must contain at least one address with which to sign messages");
        }

        final byte[][] addresses;
        try {
            addresses = JsonPack.unpackBase64PayloadsArrayToByteArrays(addressesArray, false);
        } catch (JSONException e) {
            throw new IllegalArgumentException("addresses must be an array of base64url-encoded Strings");
        }
        // throws uncaught IllegalArgumentException if addressesArray contains nulls

        if (checkExceedsSigningLimits(payloads.length, SigningType.Message)) {
            handleRpcError(id, ProtocolContract.ERROR_TOO_MANY_PAYLOADS, "number of payloads provided for signing exceeds implementation limit", null);
            return;
        }

        final SignMessagesRequest request = new SignMessagesRequest(id, payloads, addresses);
        request.notifyOnComplete((f) -> mHandler.post(() -> onSignPayloadsComplete(f)));
        mMethodHandlers.signMessages(request);
    }

    public static class SignMessagesRequest extends SignRequest<SignedPayloadsResult> {
        @NonNull
        public final byte[][] addresses;

        protected SignMessagesRequest(@Nullable Object id,
                                          @NonNull byte[][] payloads,
                                          @NonNull byte[][] addresses) {
            super(id, payloads);
            this.addresses = addresses;
        }

        @NonNull
        @Override
        public String toString() {
            return "SignMessagesRequest{" +
                    "addresses=" + Arrays.toString(addresses) +
                    ", super=" + super.toString() +
                    '}';
        }
    }

    // =============================================================================================
    // sign_and_send_transactions
    // =============================================================================================

    public static class SignAndSendTransactionsRequest extends SignRequest<SignaturesResult> {
        @Nullable
        public final Integer minContextSlot;

        private SignAndSendTransactionsRequest(@Nullable Object id,
                                               @NonNull @Size(min = 1) byte[][] transactions,
                                               @Nullable Integer minContextSlot) {
            super(id, transactions);
            this.minContextSlot = minContextSlot;
        }

        @Override
        public boolean completeExceptionally(@NonNull Exception ex) {
            if (ex instanceof NotSubmittedException) {
                final NotSubmittedException nce = (NotSubmittedException) ex;
                if (nce.signatures.length != payloads.length) {
                    throw new IllegalArgumentException("Number of signatures does not match the number of payloads");
                }
            }
            return super.completeExceptionally(ex);
        }

        @NonNull
        @Override
        public String toString() {
            return "SignAndSendTransactionsRequest{" +
                    "minContextSlot=" + minContextSlot +
                    ", super=" + super.toString() +
                    '}';
        }
    }

    public static class SignaturesResult implements SignResult {
        @NonNull
        @Size(min = 1)
        public final byte[][] signatures;

        public SignaturesResult(@NonNull @Size(min = 1) byte[][] signatures) {
            this.signatures = signatures;
        }

        @Override
        public int getNumResults() {
            return signatures.length;
        }

        @NonNull
        @Override
        public String toString() {
            return "SignaturesResult{signatures=" + Arrays.toString(signatures) + '}';
        }
    }

    private void handleSignAndSendTransactions(@Nullable Object id, @Nullable Object params)
            throws IOException {
        if (!(params instanceof JSONObject)) {
            handleRpcError(id, ERROR_INVALID_PARAMS, "params must be either a JSONObject", null);
            return;
        }

        final JSONObject o = (JSONObject) params;

        final byte[][] payloads;
        try {
            payloads = unpackPayloadsArray(o);
        } catch (IllegalArgumentException e) {
            handleRpcError(id, ERROR_INVALID_PARAMS, "request contains an invalid payloads entry", null);
            return;
        }

        if (checkExceedsSigningLimits(payloads.length, SigningType.Transaction)) {
            handleRpcError(id, ProtocolContract.ERROR_TOO_MANY_PAYLOADS, "number of transactions provided for signing exceeds implementation limit", null);
            return;
        }

        final JSONObject options = o.optJSONObject(ProtocolContract.PARAMETER_OPTIONS);

        final Integer minContextSlot;
        if (options != null && options.has(ProtocolContract.PARAMETER_OPTIONS_MIN_CONTEXT_SLOT)) {
            try {
                minContextSlot = options.getInt(ProtocolContract.PARAMETER_OPTIONS_MIN_CONTEXT_SLOT);
            } catch (JSONException e) {
                handleRpcError(id, ERROR_INVALID_PARAMS, "min_context_slot must be an integer", null);
                return;
            }
        } else {
            minContextSlot = null;
        }

        final SignAndSendTransactionsRequest request = new SignAndSendTransactionsRequest(
                id, payloads, minContextSlot);
        request.notifyOnComplete((f) -> mHandler.post(() -> onSignAndSendTransactionsComplete(f)));
        mMethodHandlers.signAndSendTransactions(request);
    }

    private void onSignAndSendTransactionsComplete(@NonNull NotifyOnCompleteFuture<SignaturesResult> future) {
        final SignAndSendTransactionsRequest request = (SignAndSendTransactionsRequest) future;

        try {
            final SignaturesResult result;
            try {
                result = request.get();
            } catch (ExecutionException e) {
                final Throwable cause = e.getCause();
                if (cause instanceof RequestDeclinedException) {
                    handleRpcError(request.id, ProtocolContract.ERROR_NOT_SIGNED, "sign request declined", null);
                } else if (cause instanceof AuthorizationNotValidException) {
                    handleRpcError(request.id, ProtocolContract.ERROR_AUTHORIZATION_FAILED, "auth_token not valid for signing", null);
                } else if (cause instanceof InvalidPayloadsException) {
                    final InvalidPayloadsException e2 = (InvalidPayloadsException) cause;
                    handleRpcError(request.id, ProtocolContract.ERROR_INVALID_PAYLOADS, "payloads invalid for signing",
                            createInvalidPayloadsData(e2.valid));
                } else if (cause instanceof NotSubmittedException) {
                    final NotSubmittedException e2 = (NotSubmittedException) cause;
                    handleRpcError(request.id, ProtocolContract.ERROR_NOT_SUBMITTED, "transactions not submitted",
                            createNotSubmittedData(e2.signatures));
                } else if (cause instanceof  TooManyPayloadsException) {
                    handleRpcError(request.id, ProtocolContract.ERROR_TOO_MANY_PAYLOADS, "number of transactions provided for signing exceeds implementation limit", null);
                } else {
                    handleRpcError(request.id, ERROR_INTERNAL, "Error while processing sign request", null);
                }
                return;
            } catch (CancellationException e) {
                // Treat cancellation as a declined request
                handleRpcError(request.id, ProtocolContract.ERROR_NOT_SIGNED, "sign request declined", null);
                return;
            } catch (InterruptedException e) {
                throw new RuntimeException("Should never occur!");
            }

            assert(result != null); // checked in SignPayloadsRequest.complete()
            assert(result.signatures.length == request.payloads.length); // checked in SignPayloadsRequest.complete()

            final JSONObject o = new JSONObject();
            try {
                o.put(ProtocolContract.RESULT_SIGNATURES,
                        JsonPack.packByteArraysToBase64PayloadsArray(result.signatures));
            } catch (JSONException e) {
                throw new RuntimeException("Failed constructing sign response", e);
            }

            handleRpcResult(request.id, o);
        } catch (IOException e) {
            Log.e(TAG, "Failed sending response for id=" + request.id, e);
        }
    }

    @NonNull
    private String createNotSubmittedData(@NonNull @Size(min = 1) byte[][] signatures) {
        final JSONObject o = new JSONObject();
        try {
            o.put(ProtocolContract.DATA_NOT_SUBMITTED_SIGNATURES,
                    JsonPack.packByteArraysToBase64PayloadsArray(signatures));
        } catch (JSONException e) {
            throw new RuntimeException("Failed constructing not submitted data", e);
        }

        return o.toString();
    }

    // =============================================================================================
    // Common exceptions
    // =============================================================================================

    public static abstract class MobileWalletAdapterServerException extends Exception {
        protected MobileWalletAdapterServerException(@NonNull String m) { super(m); }
    }

    public static class RequestDeclinedException extends MobileWalletAdapterServerException {
        public RequestDeclinedException(@NonNull String m) { super(m); }
    }

    public static class AuthorizationNotValidException extends MobileWalletAdapterServerException {
        public AuthorizationNotValidException(@NonNull String m) { super (m); }
    }

    public static class InvalidPayloadsException extends MobileWalletAdapterServerException {
        @NonNull
        @Size(min = 1)
        public final boolean[] valid;

        public InvalidPayloadsException(@NonNull String m,
                                        @NonNull @Size(min = 1) boolean[] valid) {
            super(m);
            this.valid = valid;
        }

        @Nullable
        @Override
        public String getMessage() {
            return super.getMessage() + "/valid=" + Arrays.toString(valid);
        }
    }

    public static class NotSubmittedException extends MobileWalletAdapterServerException {
        @NonNull
        @Size(min = 1)
        public final byte[][] signatures;

        public NotSubmittedException(@NonNull String m,
                                     @NonNull @Size(min = 1) byte[][] signatures) {
            super(m);
            this.signatures = signatures;
        }

        @Nullable
        @Override
        public String getMessage() {
            return super.getMessage() +
                    "/signatures=" + Arrays.toString(signatures);
        }
    }

    public static class TooManyPayloadsException extends MobileWalletAdapterServerException {
        public TooManyPayloadsException(@NonNull String m) { super(m); }
    }

    public static class ClusterNotSupportedException extends MobileWalletAdapterServerException {
        public ClusterNotSupportedException(@NonNull String m) { super(m); }
    }
}
