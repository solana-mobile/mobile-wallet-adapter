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
import androidx.annotation.Size;

import com.solana.mobilewalletadapter.common.ProtocolContract;
import com.solana.mobilewalletadapter.common.protocol.CommitmentLevel;
import com.solana.mobilewalletadapter.common.protocol.PrivilegedMethod;
import com.solana.mobilewalletadapter.common.util.JsonPack;
import com.solana.mobilewalletadapter.common.util.NotifyOnCompleteFuture;
import com.solana.mobilewalletadapter.common.util.NotifyingCompletableFuture;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

public class MobileWalletAdapterServer extends JsonRpc20Server {
    private static final String TAG = MobileWalletAdapterServer.class.getSimpleName();

    @NonNull
    private final Handler mHandler;
    @NonNull
    private final MethodHandlers mMethodHandlers;

    public interface MethodHandlers {
        void authorize(@NonNull AuthorizeRequest request);
        void reauthorize(@NonNull ReauthorizeRequest request);
        void deauthorize(@NonNull DeauthorizeRequest request);
        void signPayload(@NonNull SignPayloadRequest request);
        void signAndSendTransaction(@NonNull SignAndSendTransactionRequest request);
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
                case ProtocolContract.METHOD_SIGN_TRANSACTION:
                    handleSignPayload(id, params, SignPayloadRequest.Type.Transaction);
                    break;
                case ProtocolContract.METHOD_SIGN_MESSAGE:
                    handleSignPayload(id, params, SignPayloadRequest.Type.Message);
                    break;
                case ProtocolContract.METHOD_SIGN_AND_SEND_TRANSACTION:
                    handleSignAndSendTransaction(id, params);
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
            privilegedMethods.add(method);
        }

        final AuthorizeRequest request = new AuthorizeRequest(id, identityUri, iconUri, identityName, privilegedMethods);
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

            final JSONObject o = new JSONObject();
            try {
                o.put(ProtocolContract.RESULT_AUTH_TOKEN, result.authToken);
                o.put(ProtocolContract.RESULT_PUBLIC_KEY, result.publicKey);
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
        public final Set<PrivilegedMethod> privilegedMethods;

        private AuthorizeRequest(@Nullable Object id,
                                 @Nullable Uri identityUri,
                                 @Nullable Uri iconUri,
                                 @Nullable String identityName,
                                 @NonNull Set<PrivilegedMethod> privilegedMethods) {
            super(id);
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

        @NonNull
        @Override
        public String toString() {
            return "AuthorizeRequest{" +
                    "id=" + id +
                    ", identityUri=" + identityUri +
                    ", iconUri=" + iconUri +
                    ", identityName='" + identityName + '\'' +
                    ", privilegedMethods=" + privilegedMethods +
                    '/' + super.toString() +
                    '}';
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

        @NonNull
        @Override
        public String toString() {
            return "AuthorizeResult{" +
                    "authToken=<REDACTED>" +
                    ", publicKey='" + publicKey + '\'' +
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
                if (cause instanceof RequestDeclinedException) {
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
    // sign_* common
    // =============================================================================================

    private static abstract class SignRequest<T extends SignResult> extends RequestFuture<T> {
        @NonNull
        public final String authToken;

        @NonNull
        @Size(min = 1)
        public final byte[][] payloads;

        private SignRequest(@Nullable Object id,
                            @NonNull String authToken,
                            @NonNull @Size(min = 1) byte[][] payloads) {
            super(id);
            this.authToken = authToken;
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
            if (ex instanceof InvalidPayloadException) {
                final InvalidPayloadException ipe = (InvalidPayloadException) ex;
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
                    ", authToken='" + authToken + '\'' +
                    ", payloads=" + Arrays.toString(payloads) +
                    ", super=" + super.toString() +
                    '}';
        }
    }

    public interface SignResult {
        int getNumResults();
    }

    public static class SignPayloadRequest extends SignRequest<SignedPayloadResult> {
        public enum Type { Transaction, Message }

        @NonNull
        public final Type type;

        protected SignPayloadRequest(@Nullable Object id,
                                     @NonNull Type type,
                                     @NonNull String authToken,
                                     @NonNull byte[][] payloads) {
            super(id, authToken, payloads);
            this.type = type;
        }

        @NonNull
        @Override
        public String toString() {
            return "SignPayloadRequest{" +
                    "type=" + type +
                    ", super=" + super.toString() +
                    '}';
        }
    }

    public static class SignedPayloadResult implements SignResult {
        @NonNull
        @Size(min = 1)
        public final byte[][] signedPayloads;

        public SignedPayloadResult(@NonNull @Size(min = 1) byte[][] signedPayloads) {
            this.signedPayloads = signedPayloads;
        }

        @Override
        public int getNumResults() {
            return signedPayloads.length;
        }

        @NonNull
        @Override
        public String toString() {
            return "SignedPayloadResult{signedPayloads=" + Arrays.toString(signedPayloads) + '}';
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

    private void handleSignPayload(@Nullable Object id,
                                   @Nullable Object params,
                                   @NonNull SignPayloadRequest.Type type)
            throws IOException {
        if (!(params instanceof JSONObject)) {
            handleRpcError(id, ERROR_INVALID_PARAMS, "params must be either a JSONObject", null);
            return;
        }

        final JSONObject o = (JSONObject) params;

        final String authToken = o.optString(ProtocolContract.PARAMETER_AUTH_TOKEN);
        if (authToken.isEmpty()) {
            handleRpcError(id, ERROR_INVALID_PARAMS, "request must contain an auth_token", null);
            return;
        }

        final byte[][] payloads;
        try {
            payloads = unpackPayloadsArray(o);
        } catch (IllegalArgumentException e) {
            handleRpcError(id, ERROR_INVALID_PARAMS, "request contains an invalid payloads entry", null);
            return;
        }

        final SignPayloadRequest request = new SignPayloadRequest(id, type, authToken, payloads);
        request.notifyOnComplete((f) -> mHandler.post(() -> onSignPayloadComplete(f)));
        mMethodHandlers.signPayload(request);
    }

    private void onSignPayloadComplete(@NonNull NotifyOnCompleteFuture<SignedPayloadResult> future) {
        final SignPayloadRequest request = (SignPayloadRequest) future;

        try {
            final SignedPayloadResult result;
            try {
                result = request.get();
            } catch (ExecutionException e) {
                final Throwable cause = e.getCause();
                if (cause instanceof RequestDeclinedException) {
                    handleRpcError(request.id, ProtocolContract.ERROR_NOT_SIGNED, "sign request declined", null);
                } else if (cause instanceof ReauthorizationRequiredException) {
                    handleRpcError(request.id, ProtocolContract.ERROR_REAUTHORIZE, "auth_token requires reauthorization", null);
                } else if (cause instanceof AuthTokenNotValidException) {
                    handleRpcError(request.id, ProtocolContract.ERROR_AUTHORIZATION_FAILED, "auth_token not valid for signing", null);
                } else if (cause instanceof InvalidPayloadException) {
                    handleRpcError(request.id, ProtocolContract.ERROR_INVALID_PAYLOAD, "payload invalid for signing",
                            createInvalidPayloadData(((InvalidPayloadException) cause).valid));
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

            assert(result != null); // checked in SignPayloadRequest.complete()
            assert(result.signedPayloads.length == request.payloads.length); // checked in SignPayloadRequest.complete()

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
    private String createInvalidPayloadData(@NonNull @Size(min = 1) boolean[] valid) {
        final JSONArray arr = JsonPack.packBooleans(valid);
        final JSONObject o = new JSONObject();
        try {
            o.put(ProtocolContract.DATA_INVALID_PAYLOAD_VALID, arr);
        } catch (JSONException e) {
            throw new RuntimeException("Failed constructing invalid payload data", e);
        }

        return o.toString();
    }

    // =============================================================================================
    // sign_and_send_transaction
    // TODO: can we do a better job of merging this with sign_* above?
    // =============================================================================================

    public static class SignAndSendTransactionRequest extends SignRequest<SignatureResult> {
        @NonNull
        public final CommitmentLevel commitmentLevel;

        private SignAndSendTransactionRequest(@Nullable Object id,
                                              @NonNull String authToken,
                                              @NonNull @Size(min = 1) byte[][] transactions,
                                              @NonNull CommitmentLevel commitmentLevel) {
            super(id, authToken, transactions);
            this.commitmentLevel = commitmentLevel;
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
            return "SignAndSendTransactionRequest{" +
                    "commitmentLevel=" + commitmentLevel +
                    ", super=" + super.toString() +
                    '}';
        }
    }

    public static class SignatureResult implements SignResult {
        @NonNull
        @Size(min = 1)
        public final byte[][] signatures;

        public SignatureResult(@NonNull @Size(min = 1) byte[][] signatures) {
            this.signatures = signatures;
        }

        @Override
        public int getNumResults() {
            return signatures.length;
        }

        @NonNull
        @Override
        public String toString() {
            return "SignatureResult{signedPayloads=" + Arrays.toString(signatures) + '}';
        }
    }

    private void handleSignAndSendTransaction(@Nullable Object id, @Nullable Object params)
            throws IOException {
        if (!(params instanceof JSONObject)) {
            handleRpcError(id, ERROR_INVALID_PARAMS, "params must be either a JSONObject", null);
            return;
        }

        final JSONObject o = (JSONObject) params;

        final String authToken = o.optString(ProtocolContract.PARAMETER_AUTH_TOKEN);
        if (authToken.isEmpty()) {
            handleRpcError(id, ERROR_INVALID_PARAMS, "request must contain an auth_token", null);
            return;
        }

        final byte[][] payloads;
        try {
            payloads = unpackPayloadsArray(o);
        } catch (IllegalArgumentException e) {
            handleRpcError(id, ERROR_INVALID_PARAMS, "request contains an invalid payloads entry", null);
            return;
        }

        final String commitmentLevelStr = o.optString(ProtocolContract.PARAMETER_COMMITMENT);
        final CommitmentLevel commitmentLevel = CommitmentLevel.fromCommitmentLevelString(
                commitmentLevelStr);
        if (commitmentLevel == null) {
            handleRpcError(id, ERROR_INVALID_PARAMS, "request contains an invalid commitment_level", null);
            return;
        }

        final SignAndSendTransactionRequest request = new SignAndSendTransactionRequest(
                id, authToken, payloads, commitmentLevel);
        request.notifyOnComplete((f) -> mHandler.post(() -> onSignAndSendTransactionComplete(f)));
        mMethodHandlers.signAndSendTransaction(request);
    }

    private void onSignAndSendTransactionComplete(@NonNull NotifyOnCompleteFuture<SignatureResult> future) {
        final SignAndSendTransactionRequest request = (SignAndSendTransactionRequest) future;

        try {
            final SignatureResult result;
            try {
                result = request.get();
            } catch (ExecutionException e) {
                final Throwable cause = e.getCause();
                if (cause instanceof RequestDeclinedException) {
                    handleRpcError(request.id, ProtocolContract.ERROR_NOT_SIGNED, "sign request declined", null);
                } else if (cause instanceof ReauthorizationRequiredException) {
                    handleRpcError(request.id, ProtocolContract.ERROR_REAUTHORIZE, "auth_token requires reauthorization", null);
                } else if (cause instanceof AuthTokenNotValidException) {
                    handleRpcError(request.id, ProtocolContract.ERROR_AUTHORIZATION_FAILED, "auth_token not valid for signing", null);
                } else if (cause instanceof InvalidPayloadException) {
                    final InvalidPayloadException e2 = (InvalidPayloadException) cause;
                    handleRpcError(request.id, ProtocolContract.ERROR_INVALID_PAYLOAD, "payload invalid for signing",
                            createInvalidPayloadData(e2.valid));
                } else if (cause instanceof NotCommittedException) {
                    final NotCommittedException e2 = (NotCommittedException) cause;
                    handleRpcError(request.id, ProtocolContract.ERROR_NOT_COMMITTED, "transaction not committed",
                            createNotCommittedData(e2.signatures, e2.committed));
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

            assert(result != null); // checked in SignPayloadRequest.complete()
            assert(result.signatures.length == request.payloads.length); // checked in SignPayloadRequest.complete()

            final JSONArray signatures = JsonPack.packByteArraysToBase64PayloadsArray(result.signatures);
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
        final JSONArray signaturesArr = JsonPack.packByteArraysToBase64PayloadsArray(signatures);
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

        public InvalidPayloadException(@NonNull String m,
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
}
