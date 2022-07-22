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
import com.solana.mobilewalletadapter.common.protocol.CommitmentLevel;
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
        void signPayloads(@NonNull SignPayloadsRequest request);
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
                    handleSignPayloads(id, params, SignPayloadsRequest.Type.Transaction);
                    break;
                case ProtocolContract.METHOD_SIGN_MESSAGES:
                    handleSignPayloads(id, params, SignPayloadsRequest.Type.Message);
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
        request.notifyOnComplete((f) -> mHandler.post(() -> onAuthorizeComplete(f)));
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
                final JSONArray addresses = new JSONArray(); // TODO(#44): support multiple addresses
                addresses.put(publicKeyBase64);
                o.put(ProtocolContract.RESULT_ADDRESSES, addresses);
                o.put(ProtocolContract.RESULT_WALLET_URI_BASE, result.walletUriBase); // OK if null
            } catch (JSONException e) {
                throw new RuntimeException("Failed preparing authorize response", e);
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
        public final String cluster;

        private AuthorizeRequest(@Nullable Object id,
                                 @Nullable Uri identityUri,
                                 @Nullable Uri iconUri,
                                 @Nullable String identityName,
                                 @NonNull String cluster) {
            super(id);
            this.identityUri = identityUri;
            this.iconUri = iconUri;
            this.identityName = identityName;
            this.cluster = cluster;
        }

        @Override
        public boolean complete(@Nullable AuthorizeResult result) {
            if (result == null) {
                throw new IllegalArgumentException("A non-null result must be provided");
            }
            return super.complete(result);
        }

        @NonNull
        @Override
        public String toString() {
            return "AuthorizeRequest{" +
                    "id=" + id +
                    ", identityUri=" + identityUri +
                    ", iconUri=" + iconUri +
                    ", identityName='" + identityName + '\'' +
                    ", cluster='" + cluster + '\'' +
                    '/' + super.toString() +
                    '}';
        }
    }

    public static class AuthorizeResult {
        @NonNull
        public final String authToken;

        @NonNull
        public final byte[] publicKey;

        @Nullable
        public final Uri walletUriBase;

        public AuthorizeResult(@NonNull String authToken,
                               @NonNull byte[] publicKey,
                               @Nullable Uri walletUriBase) {
            this.authToken = authToken;
            this.publicKey = publicKey;
            this.walletUriBase = walletUriBase;
        }

        @NonNull
        @Override
        public String toString() {
            return "AuthorizeResult{" +
                    "authToken=<REDACTED>" +
                    ", publicKey=" + Arrays.toString(publicKey) +
                    ", walletUriBase=" + walletUriBase +
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
        request.notifyOnComplete((f) -> mHandler.post(() -> onReauthorizeComplete(f)));
        mMethodHandlers.reauthorize(request);
    }

    private void onReauthorizeComplete(@NonNull NotifyOnCompleteFuture<ReauthorizeResult> future) {
        final ReauthorizeRequest request = (ReauthorizeRequest) future;

        try {
            final ReauthorizeResult result;
            try {
                result = request.get();
            } catch (ExecutionException e) {
                final Throwable cause = e.getCause();
                if (
                    cause instanceof AuthorizationNotValidException ||
                    cause instanceof RequestDeclinedException
                ) {
                    handleRpcError(request.id, ProtocolContract.ERROR_AUTHORIZATION_FAILED, "reauthorize request failed", null);
                } else {
                    handleRpcError(request.id, ERROR_INTERNAL, "Error while processing reauthorize request", null);
                }
                return;
            } catch (CancellationException e) {
                // Treat cancellation as a declined request
                handleRpcError(request.id, ProtocolContract.ERROR_AUTHORIZATION_FAILED, "reauthorize request failed", null);
                return;
            } catch (InterruptedException e) {
                throw new RuntimeException("Should never occur!");
            }

            assert(result != null); // checked in ReauthorizeRequest.complete()

            final JSONObject o = new JSONObject();
            try {
                o.put(ProtocolContract.RESULT_AUTH_TOKEN, result.authToken);
            } catch (JSONException e) {
                throw new RuntimeException("Failed preparing reauthorize response", e);
            }

            handleRpcResult(request.id, o);
        } catch (IOException e) {
            Log.e(TAG, "Failed sending response for id=" + request.id, e);
        }
    }

    public static class ReauthorizeRequest extends RequestFuture<ReauthorizeResult> {
        @Nullable
        public final Uri identityUri;
        @Nullable
        public final Uri iconUri;
        @Nullable
        public final String identityName;
        @NonNull
        public final String authToken;

        private ReauthorizeRequest(@Nullable Object id,
                                   @Nullable Uri identityUri,
                                   @Nullable Uri iconUri,
                                   @Nullable String identityName,
                                   @NonNull String authToken) {
            super(id);
            this.identityUri = identityUri;
            this.iconUri = iconUri;
            this.identityName = identityName;
            this.authToken = authToken;
        }

        @Override
        public boolean complete(@Nullable ReauthorizeResult result) {
            if (result == null) {
                throw new IllegalArgumentException("A non-null result must be provided");
            }
            return super.complete(result);
        }

        @NonNull
        @Override
        public String toString() {
            return "ReauthorizeRequest{" +
                    "id=" + id +
                    ", identityUri=" + identityUri +
                    ", iconUri=" + iconUri +
                    ", identityName='" + identityName + '\'' +
                    ", authToken=<REDACTED>" +
                    '/' + super.toString() +
                    '}';
        }
    }

    public static class ReauthorizeResult {
        @NonNull
        public final String authToken;

        public ReauthorizeResult(@NonNull String authToken) {
            this.authToken = authToken;
        }

        @NonNull
        @Override
        public String toString() {
            return "ReauthorizeResult{authToken=<REDACTED>}";
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

    private static abstract class SignRequest<T extends SignResult> extends RequestFuture<T> {
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

    public static class SignPayloadsRequest extends SignRequest<SignedPayloadsResult> {
        public enum Type { Transaction, Message }

        @NonNull
        public final Type type;

        protected SignPayloadsRequest(@Nullable Object id,
                                      @NonNull Type type,
                                      @NonNull byte[][] payloads) {
            super(id, payloads);
            this.type = type;
        }

        @NonNull
        @Override
        public String toString() {
            return "SignPayloadsRequest{" +
                    "type=" + type +
                    ", super=" + super.toString() +
                    '}';
        }
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
            payloads = JsonPack.unpackBase64PayloadsArrayToByteArrays(payloadsArray);
        } catch (JSONException e) {
            throw new IllegalArgumentException("payloads must be an array of base64url-encoded Strings");
        }

        return payloads;
    }

    private void handleSignPayloads(@Nullable Object id,
                                    @Nullable Object params,
                                    @NonNull SignPayloadsRequest.Type type)
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

        if (checkExceedsSigningLimits(payloads.length, type)) {
            handleRpcError(id, ProtocolContract.ERROR_TOO_MANY_PAYLOADS, "number of payloads provided for signing exceeds implementation limit", null);
            return;
        }

        final SignPayloadsRequest request = new SignPayloadsRequest(id, type, payloads);
        request.notifyOnComplete((f) -> mHandler.post(() -> onSignPayloadsComplete(f)));
        mMethodHandlers.signPayloads(request);
    }

    private void onSignPayloadsComplete(@NonNull NotifyOnCompleteFuture<SignedPayloadsResult> future) {
        final SignPayloadsRequest request = (SignPayloadsRequest) future;

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
                                              @NonNull SignPayloadsRequest.Type type) {
        final int limit;
        switch (type) {
            case Transaction:
                limit = mConfig.maxTransactionsPerSigningRequest;
                break;
            case Message:
                limit = mConfig.maxMessagesPerSigningRequest;
                break;
            default:
                throw new RuntimeException("Unexpected transaction type: " + type);
        }
        return (limit != 0 && numPayloads > limit);
    }

    // =============================================================================================
    // sign_and_send_transactions
    // =============================================================================================

    public static class SignAndSendTransactionsRequest extends SignRequest<SignaturesResult> {
        @NonNull
        public final CommitmentLevel commitmentLevel;

        public final boolean skipPreflight;

        @NonNull
        public final CommitmentLevel preflightCommitmentLevel;

        private SignAndSendTransactionsRequest(@Nullable Object id,
                                               @NonNull @Size(min = 1) byte[][] transactions,
                                               @NonNull CommitmentLevel commitmentLevel,
                                               boolean skipPreflight,
                                               @NonNull CommitmentLevel preflightCommitmentLevel) {
            super(id, transactions);
            this.commitmentLevel = commitmentLevel;
            this.skipPreflight = skipPreflight;
            this.preflightCommitmentLevel = preflightCommitmentLevel;
        }

        @Override
        public boolean completeExceptionally(@NonNull Exception ex) {
            if (ex instanceof NotCommittedException) {
                final NotCommittedException nce = (NotCommittedException) ex;
                if (nce.signatures.length != payloads.length) {
                    throw new IllegalArgumentException("Number of signatures does not match the number of payloads");
                } else if (nce.committed.length != payloads.length) {
                    throw new IllegalArgumentException("Number of committed values does not match the number of payloads");
                }
            }
            return super.completeExceptionally(ex);
        }

        @NonNull
        @Override
        public String toString() {
            return "SignAndSendTransactionsRequest{" +
                    "commitmentLevel=" + commitmentLevel +
                    ", skipPreflight=" + skipPreflight +
                    ", preflightCommitmentLevel=" + preflightCommitmentLevel +
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

        if (checkExceedsSigningLimits(payloads.length, SignPayloadsRequest.Type.Transaction)) {
            handleRpcError(id, ProtocolContract.ERROR_TOO_MANY_PAYLOADS, "number of transactions provided for signing exceeds implementation limit", null);
            return;
        }

        final String commitmentLevelStr = o.optString(ProtocolContract.PARAMETER_COMMITMENT);
        final CommitmentLevel commitmentLevel = CommitmentLevel.fromCommitmentLevelString(
                commitmentLevelStr);
        if (commitmentLevel == null) {
            handleRpcError(id, ERROR_INVALID_PARAMS, "request contains an invalid commitment_level", null);
            return;
        }

        final boolean skipPreflight = o.optBoolean(ProtocolContract.PARAMETER_SKIP_PREFLIGHT, false);

        final CommitmentLevel preflightCommitmentLevel;
        if (o.has(ProtocolContract.PARAMETER_PREFLIGHT_COMMITMENT)) {
            final String preflightCommitmentLevelStr = o.optString(ProtocolContract.PARAMETER_PREFLIGHT_COMMITMENT);
            preflightCommitmentLevel = CommitmentLevel.fromCommitmentLevelString(preflightCommitmentLevelStr);
            if (preflightCommitmentLevel == null) {
                handleRpcError(id, ERROR_INVALID_PARAMS, "request contains an invalid preflight_commitment", null);
                return;
            }
        } else {
            preflightCommitmentLevel = CommitmentLevel.Finalized;
        }

        final SignAndSendTransactionsRequest request = new SignAndSendTransactionsRequest(
                id, payloads, commitmentLevel, skipPreflight, preflightCommitmentLevel);
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
                } else if (cause instanceof NotCommittedException) {
                    final NotCommittedException e2 = (NotCommittedException) cause;
                    handleRpcError(request.id, ProtocolContract.ERROR_NOT_COMMITTED, "transactions not committed",
                            createNotCommittedData(e2.signatures, e2.committed));
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

            final String[] signaturesBase64 = new String[result.signatures.length];
            for (int i = 0; i < result.signatures.length; i++) {
                signaturesBase64[i] = Base64.encodeToString(result.signatures[i], Base64.NO_WRAP);
            }

            final JSONArray signatures = JsonPack.packStrings(signaturesBase64);
            final JSONObject o = new JSONObject();
            try {
                o.put(ProtocolContract.RESULT_SIGNATURES, signatures);
            } catch (JSONException e) {
                throw new RuntimeException("Failed constructing sign response", e);
            }

            handleRpcResult(request.id, o);
        } catch (IOException e) {
            Log.e(TAG, "Failed sending response for id=" + request.id, e);
        }
    }

    @NonNull
    private String createNotCommittedData(@NonNull @Size(min = 1) byte[][] signatures,
                                          @NonNull @Size(min = 1) boolean[] committed) {
        final String[] signaturesBase64 = new String[signatures.length];
        for (int i = 0; i < signatures.length; i++) {
            signaturesBase64[i] = Base64.encodeToString(signatures[i], Base64.NO_WRAP);
        }

        final JSONArray signaturesArr = JsonPack.packStrings(signaturesBase64);
        final JSONArray committedArr = JsonPack.packBooleans(committed);
        final JSONObject o = new JSONObject();
        try {
            o.put(ProtocolContract.DATA_NOT_COMMITTED_SIGNATURES, signaturesArr);
            o.put(ProtocolContract.DATA_NOT_COMMITTED_COMMITMENT, committedArr);
        } catch (JSONException e) {
            throw new RuntimeException("Failed constructing not committed data", e);
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

    public static class NotCommittedException extends MobileWalletAdapterServerException {
        @NonNull
        @Size(min = 1)
        public final byte[][] signatures;

        @NonNull
        @Size(min = 1)
        public final boolean[] committed;

        public NotCommittedException(@NonNull String m,
                                     @NonNull @Size(min = 1) byte[][] signatures,
                                     @NonNull @Size(min = 1) boolean[] committed) {
            super(m);
            this.signatures = signatures;
            this.committed = committed;
        }

        @Nullable
        @Override
        public String getMessage() {
            return super.getMessage() +
                    "/signatures=" + Arrays.toString(signatures) +
                    "/committed=" + Arrays.toString(committed);
        }
    }

    public static class TooManyPayloadsException extends MobileWalletAdapterServerException {
        public TooManyPayloadsException(@NonNull String m) { super(m); }
    }
}
