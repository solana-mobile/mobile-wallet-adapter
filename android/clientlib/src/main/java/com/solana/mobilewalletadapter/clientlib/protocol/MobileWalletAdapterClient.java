/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.clientlib.protocol;

import android.net.Uri;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Size;

import com.solana.mobilewalletadapter.common.ProtocolContract;
import com.solana.mobilewalletadapter.common.protocol.CommitmentLevel;
import com.solana.mobilewalletadapter.common.util.JsonPack;
import com.solana.mobilewalletadapter.common.util.NotifyOnCompleteFuture;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class MobileWalletAdapterClient extends JsonRpc20Client {
    @IntRange(from = 0)
    private final int mClientTimeoutMs;

    public MobileWalletAdapterClient(@IntRange(from = 0) int clientTimeoutMs) {
        mClientTimeoutMs = clientTimeoutMs;
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
        public T get() throws ExecutionException, CancellationException, InterruptedException {
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
                throws ExecutionException, CancellationException, InterruptedException, TimeoutException {
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

        @NonNull
        @Override
        public String toString() {
            return "JsonRpc20MethodResultFuture{mMethodCallFuture=" + mMethodCallFuture + '}';
        }
    }

    // =============================================================================================
    // authorize
    // =============================================================================================

    @NonNull
    public AuthorizeFuture authorize(@Nullable Uri identityUri,
                                     @Nullable Uri iconUri,
                                     @Nullable String identityName)
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
            authorize = new JSONObject();
            authorize.put(ProtocolContract.PARAMETER_IDENTITY, identity);
        } catch (JSONException e) {
            throw new UnsupportedOperationException("Failed to create authorize JSON params", e);
        }

        return new AuthorizeFuture(methodCall(ProtocolContract.METHOD_AUTHORIZE, authorize, mClientTimeoutMs));
    }

    public static class AuthorizeResult {
        @NonNull
        public final String authToken;
        @NonNull
        public final String publicKey;
        @Nullable
        public final Uri walletUriBase;

        private AuthorizeResult(@NonNull String authToken,
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
                    "authToken='<REDACTED>'" +
                    ", publicKey='" + publicKey + '\'' +
                    ", walletUriBase=" + walletUriBase +
                    '}';
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
    // reauthorize
    // =============================================================================================

    @NonNull
    public ReauthorizeFuture reauthorize(@Nullable Uri identityUri,
                                         @Nullable Uri iconUri,
                                         @Nullable String identityName,
                                         @NonNull String authToken)
            throws IOException {
        if (identityUri != null && (!identityUri.isAbsolute() || !identityUri.isHierarchical())) {
            throw new IllegalArgumentException("If non-null, identityUri must be an absolute, hierarchical Uri");
        } else if (iconUri != null && !iconUri.isRelative()) {
            throw new IllegalArgumentException("If non-null, iconRelativeUri must be a relative Uri");
        }

        final JSONObject reauthorize;
        try {
            final JSONObject identity = new JSONObject();
            identity.put(ProtocolContract.PARAMETER_IDENTITY_URI, identityUri);
            identity.put(ProtocolContract.PARAMETER_IDENTITY_ICON, iconUri);
            identity.put(ProtocolContract.PARAMETER_IDENTITY_NAME, identityName);
            reauthorize = new JSONObject();
            reauthorize.put(ProtocolContract.PARAMETER_IDENTITY, identity);
            reauthorize.put(ProtocolContract.PARAMETER_AUTH_TOKEN, authToken);
        } catch (JSONException e) {
            throw new UnsupportedOperationException("Failed to create reauthorize JSON params", e);
        }

        return new ReauthorizeFuture(methodCall(ProtocolContract.METHOD_REAUTHORIZE, reauthorize, mClientTimeoutMs));
    }

    public static class ReauthorizeResult {
        @NonNull
        public final String authToken;

        private ReauthorizeResult(@NonNull String authToken) {
            this.authToken = authToken;
        }

        @NonNull
        @Override
        public String toString() {
            return "ReauthorizeResult{authToken='<REDACTED>'}";
        }
    }

    public static class ReauthorizeFuture
            extends JsonRpc20MethodResultFuture<ReauthorizeResult>
            implements NotifyOnCompleteFuture<ReauthorizeResult> {
        private ReauthorizeFuture(@NonNull NotifyOnCompleteFuture<Object> methodCallFuture) {
            super(methodCallFuture);
        }

        @NonNull
        @Override
        protected ReauthorizeResult processResult(@Nullable Object o)
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

            return new ReauthorizeResult(authToken);
        }

        @Override
        public void notifyOnComplete(@NonNull OnCompleteCallback<? super NotifyOnCompleteFuture<ReauthorizeResult>> cb) {
            mMethodCallFuture.notifyOnComplete((f) -> cb.onComplete(this));
        }
    }

    // =============================================================================================
    // deauthorize
    // =============================================================================================

    @NonNull
    public DeauthorizeFuture deauthorize(@NonNull String authToken)
            throws IOException {
        final JSONObject deauthorize;
        try {
            deauthorize = new JSONObject();
            deauthorize.put(ProtocolContract.PARAMETER_AUTH_TOKEN, authToken);
        } catch (JSONException e) {
            throw new UnsupportedOperationException("Failed to create deauthorize JSON params", e);
        }

        return new DeauthorizeFuture(methodCall(ProtocolContract.METHOD_DEAUTHORIZE, deauthorize, mClientTimeoutMs));
    }

    public static class DeauthorizeFuture
            extends JsonRpc20MethodResultFuture<Object>
            implements NotifyOnCompleteFuture<Object> {
        private DeauthorizeFuture(@NonNull NotifyOnCompleteFuture<Object> methodCallFuture) {
            super(methodCallFuture);
        }

        @NonNull
        @Override
        protected Object processResult(@Nullable Object o) {
            return new Object();
        }

        @Override
        public void notifyOnComplete(@NonNull OnCompleteCallback<? super NotifyOnCompleteFuture<Object>> cb) {
            mMethodCallFuture.notifyOnComplete((f) -> cb.onComplete(this));
        }
    }

    // =============================================================================================
    // get_capabilities
    // =============================================================================================

    @NonNull
    public GetCapabilitiesFuture getCapabilities()
            throws IOException {
        final JSONObject params = new JSONObject();
        return new GetCapabilitiesFuture(methodCall(ProtocolContract.METHOD_GET_CAPABILITIES,
                params, mClientTimeoutMs));
    }

    public static class GetCapabilitiesResult {
        public final boolean supportsCloneAuthorization;

        public final boolean supportsSignAndSendTransactions;

        @IntRange(from = 0)
        public final int maxTransactionsPerSigningRequest;

        @IntRange(from = 0)
        public final int maxMessagesPerSigningRequest;

        private GetCapabilitiesResult(boolean supportsCloneAuthorization,
                                      boolean supportsSignAndSendTransactions,
                                      @IntRange(from = 0) int maxTransactionsPerSigningRequest,
                                      @IntRange(from = 0) int maxMessagesPerSigningRequest) {
            this.supportsCloneAuthorization = supportsCloneAuthorization;
            this.supportsSignAndSendTransactions = supportsSignAndSendTransactions;
            this.maxTransactionsPerSigningRequest = maxTransactionsPerSigningRequest;
            this.maxMessagesPerSigningRequest = maxMessagesPerSigningRequest;
        }

        @NonNull
        @Override
        public String toString() {
            return "GetCapabilitiesResult{" +
                    "supportsCloneAuthorization=" + supportsCloneAuthorization +
                    ", supportsSignAndSendTransactions=" + supportsSignAndSendTransactions +
                    ", maxTransactionsPerSigningRequest=" + maxTransactionsPerSigningRequest +
                    ", maxMessagesPerSigningRequest=" + maxMessagesPerSigningRequest +
                    '}';
        }
    }

    public static class GetCapabilitiesFuture
            extends JsonRpc20MethodResultFuture<GetCapabilitiesResult>
            implements NotifyOnCompleteFuture<GetCapabilitiesResult> {
        private GetCapabilitiesFuture(@NonNull NotifyOnCompleteFuture<Object> methodCallFuture) {
            super(methodCallFuture);
        }

        @NonNull
        @Override
        protected GetCapabilitiesResult processResult(@Nullable Object o)
                throws JsonRpc20InvalidResponseException {
            if (!(o instanceof JSONObject)) {
                throw new JsonRpc20InvalidResponseException("expected result to be a JSON object");
            }

            final JSONObject jo = (JSONObject) o;

            final boolean supportsCloneAuthorization;
            final boolean supportsSignAndSendTransactions;
            final int maxTransactionsPerSigningRequest;
            final int maxMessagesPerSigningRequest;
            try {
                supportsCloneAuthorization = jo.getBoolean(ProtocolContract.RESULT_SUPPORTS_CLONE_AUTHORIZATION);
                supportsSignAndSendTransactions = jo.getBoolean(ProtocolContract.RESULT_SUPPORTS_SIGN_AND_SEND_TRANSACTIONS);
                maxTransactionsPerSigningRequest = jo.optInt(ProtocolContract.RESULT_MAX_TRANSACTIONS_PER_REQUEST, 0);
                maxMessagesPerSigningRequest = jo.optInt(ProtocolContract.RESULT_MAX_MESSAGES_PER_REQUEST, 0);
            } catch (JSONException e) {
                throw new JsonRpc20InvalidResponseException("result does not conform to expected format");
            }

            return new GetCapabilitiesResult(supportsCloneAuthorization,
                    supportsSignAndSendTransactions,
                    maxTransactionsPerSigningRequest,
                    maxMessagesPerSigningRequest);
        }

        @Override
        public void notifyOnComplete(@NonNull OnCompleteCallback<? super NotifyOnCompleteFuture<GetCapabilitiesResult>> cb) {
            mMethodCallFuture.notifyOnComplete((f) -> cb.onComplete(this));
        }
    }

    // =============================================================================================
    // sign_* common
    // =============================================================================================

    @NonNull
    private NotifyOnCompleteFuture<Object> signPayloads(@NonNull String method,
                                                        @NonNull String authToken,
                                                        @NonNull @Size(min = 1) byte[][] payloads)
            throws IOException {
        if (authToken.isEmpty()) {
            throw new IllegalArgumentException("authToken cannot be empty");
        }
        for (byte[] p : payloads) {
            if (p == null || p.length == 0) {
                throw new IllegalArgumentException("payloads must be null or empty");
            }
        }

        final JSONArray payloadsArr = JsonPack.packByteArraysToBase64PayloadsArray(payloads);
        final JSONObject signPayloads = new JSONObject();
        try {
            signPayloads.put(ProtocolContract.PARAMETER_AUTH_TOKEN, authToken);
            signPayloads.put(ProtocolContract.PARAMETER_PAYLOADS, payloadsArr);
        } catch (JSONException e) {
            throw new UnsupportedOperationException("Failed to create signing payload JSON params", e);
        }

        return methodCall(method, signPayloads, mClientTimeoutMs);
    }

    @NonNull
    @Size(min = 1)
    private static byte[][] unpackResponsePayloadArray(@NonNull JSONObject jo,
                                                       @NonNull String paramName,
                                                       @IntRange(from = 1) int numExpectedPayloads)
            throws JsonRpc20InvalidResponseException {
        assert(numExpectedPayloads > 0); // checked with inputs to sign*

        final JSONArray arr;
        try {
            arr = jo.getJSONArray(paramName);
        } catch (JSONException e) {
            throw new JsonRpc20InvalidResponseException("JSON object does not contain a valid array");
        }
        final int numPayloads = arr.length();
        if (numPayloads != numExpectedPayloads) {
            throw new JsonRpc20InvalidResponseException(paramName + " should contain " +
                    numExpectedPayloads + " entries; actual=" + numPayloads);
        }

        final byte[][] payloads;
        try {
            payloads = JsonPack.unpackBase64PayloadsArrayToByteArrays(arr);
        } catch (JSONException e) {
            throw new JsonRpc20InvalidResponseException(paramName + " must be an array of base64url-encoded Strings");
        }

        return payloads;
    }

    @NonNull
    @Size(min = 1)
    private static boolean[] unpackResponseBooleanArray(@NonNull JSONObject jo,
                                                        @NonNull String paramName,
                                                        @IntRange(from = 1) int numExpectedBooleans)
            throws JsonRpc20InvalidResponseException {
        assert(numExpectedBooleans > 0); // checked with inputs to sign*

        final JSONArray arr;
        try {
            arr = jo.getJSONArray(paramName);
        } catch (JSONException e) {
            throw new JsonRpc20InvalidResponseException("JSON object does not contain a valid array");
        }
        final int numValid = arr.length();
        if (numValid != numExpectedBooleans) {
            throw new JsonRpc20InvalidResponseException(paramName + " should contain " +
                    numExpectedBooleans + " entries; actual=" + numValid);
        }

        final boolean[] valid;
        try {
            valid = JsonPack.unpackBooleans(arr);
        } catch (JSONException e) {
            throw new JsonRpc20InvalidResponseException(paramName + " must be an array of Booleans");
        }

        return valid;
    }

    @NonNull
    @Size(min = 1)
    private static String[] unpackResponseStringArray(@NonNull JSONObject jo,
                                                      @NonNull String paramName,
                                                      @IntRange(from = 1) int numExpectedStrings)
            throws JsonRpc20InvalidResponseException {
        assert(numExpectedStrings > 0); // checked with inputs to sign*

        final JSONArray arr;
        try {
            arr = jo.getJSONArray(paramName);
        } catch (JSONException e) {
            throw new JsonRpc20InvalidResponseException("JSON object does not contain a valid array");
        }
        final int numValid = arr.length();
        if (numValid != numExpectedStrings) {
            throw new JsonRpc20InvalidResponseException(paramName + " should contain " +
                    numExpectedStrings + " entries; actual=" + numValid);
        }

        final String[] strings;
        try {
            strings = JsonPack.unpackStrings(arr);
        } catch (JSONException e) {
            throw new JsonRpc20InvalidResponseException(paramName + " must be an array of Strings");
        }

        return strings;
    }

    public static class SignPayloadsResult {
        @NonNull
        @Size(min = 1)
        public final byte[][] signedPayloads;

        public SignPayloadsResult(@NonNull @Size(min = 1) byte[][] signedPayloads) {
            this.signedPayloads = signedPayloads;
        }

        @NonNull
        @Override
        public String toString() {
            return "SignPayloadsResult{signedPayloads=" + Arrays.toString(signedPayloads) + '}';
        }
    }

    public static class SignPayloadsFuture
            extends JsonRpc20MethodResultFuture<SignPayloadsResult>
            implements NotifyOnCompleteFuture<SignPayloadsResult> {
        @IntRange(from = 1)
        private final int mExpectedNumSignedPayloads;

        private SignPayloadsFuture(@NonNull NotifyOnCompleteFuture<Object> methodCallFuture,
                                   @IntRange(from = 1) int expectedNumSignedPayloads) {
            super(methodCallFuture);
            mExpectedNumSignedPayloads = expectedNumSignedPayloads;
        }

        @NonNull
        @Override
        protected SignPayloadsResult processResult(@Nullable Object o)
                throws JsonRpc20InvalidResponseException {
            if (!(o instanceof JSONObject)) {
                throw new JsonRpc20InvalidResponseException("expected result to be a JSON object");
            }
            final JSONObject jo = (JSONObject) o;
            final byte[][] signedPayloads = unpackResponsePayloadArray(jo,
                    ProtocolContract.RESULT_SIGNED_PAYLOADS, mExpectedNumSignedPayloads);
            return new SignPayloadsResult(signedPayloads);
        }

        @Nullable
        @Override
        protected JsonRpc20Exception processRemoteException(@NonNull JsonRpc20RemoteException remoteException) {
            if (remoteException.code != ProtocolContract.ERROR_INVALID_PAYLOADS) {
                return null;
            }
            try {
                return new InvalidPayloadsException(remoteException.getMessage(),
                        remoteException.data, mExpectedNumSignedPayloads);
            } catch (JsonRpc20InvalidResponseException e) {
                return e;
            }
        }

        @Override
        public void notifyOnComplete(@NonNull OnCompleteCallback<? super NotifyOnCompleteFuture<SignPayloadsResult>> cb) {
            mMethodCallFuture.notifyOnComplete((f) -> cb.onComplete(this));
        }
    }

    public static class InvalidPayloadsException extends JsonRpc20RemoteException {
        @NonNull
        @Size(min = 1)
        public final boolean[] validPayloads;

        private InvalidPayloadsException(@NonNull String message,
                                         @Nullable String data,
                                         @IntRange(from = 1) int expectedNumSignedPayloads)
                throws JsonRpc20InvalidResponseException {
            super(ProtocolContract.ERROR_INVALID_PAYLOADS, message, data);

            if (data == null) {
                throw new JsonRpc20InvalidResponseException("data should not be null");
            }
            final JSONObject o;
            try {
                o = new JSONObject(data);
            } catch (JSONException e) {
                throw new JsonRpc20InvalidResponseException("data is not a valid ERROR_INVALID_PAYLOADS result");
            }
            validPayloads = unpackResponseBooleanArray(o,
                    ProtocolContract.DATA_INVALID_PAYLOADS_VALID, expectedNumSignedPayloads);
        }

        @NonNull
        @Override
        public String getMessage() {
            return super.getMessage() + "/validPayloads=" + Arrays.toString(validPayloads);
        }
    }

    // =============================================================================================
    // sign_transactions
    // =============================================================================================

    @NonNull
    public SignPayloadsFuture signTransactions(@NonNull String authToken,
                                               @NonNull @Size(min = 1) byte[][] transactions)
            throws IOException {
        return new SignPayloadsFuture(
                signPayloads(ProtocolContract.METHOD_SIGN_TRANSACTIONS, authToken, transactions),
                transactions.length);
    }

    // =============================================================================================
    // sign_messages
    // =============================================================================================

    @NonNull
    public SignPayloadsFuture signMessages(@NonNull String authToken,
                                           @NonNull @Size(min = 1) byte[][] messages)
            throws IOException {
        return new SignPayloadsFuture(
                signPayloads(ProtocolContract.METHOD_SIGN_MESSAGES, authToken, messages),
                messages.length);
    }

    // =============================================================================================
    // sign_and_send_transactions
    // =============================================================================================

    @NonNull
    public SignAndSendTransactionsFuture signAndSendTransactions(@NonNull String authToken,
                                                                 @NonNull @Size(min = 1) byte[][] transactions,
                                                                 @NonNull CommitmentLevel commitmentLevel,
                                                                 @Nullable String cluster,
                                                                 boolean skipPreflight,
                                                                 @Nullable CommitmentLevel preflightCommitmentLevel)
            throws IOException {
        if (authToken.isEmpty()) {
            throw new IllegalArgumentException("authToken cannot be empty");
        }
        for (byte[] t : transactions) {
            if (t == null || t.length == 0) {
                throw new IllegalArgumentException("transactions must be null or empty");
            }
        }

        final JSONArray payloadsArr = JsonPack.packByteArraysToBase64PayloadsArray(transactions);
        final JSONObject signAndSendTransactions = new JSONObject();
        try {
            signAndSendTransactions.put(ProtocolContract.PARAMETER_AUTH_TOKEN, authToken);
            signAndSendTransactions.put(ProtocolContract.PARAMETER_PAYLOADS, payloadsArr);
            signAndSendTransactions.put(ProtocolContract.PARAMETER_COMMITMENT,
                    commitmentLevel.commitmentLevel);
            signAndSendTransactions.put(ProtocolContract.PARAMETER_CLUSTER, cluster); // null is OK
            if (skipPreflight) {
                signAndSendTransactions.put(ProtocolContract.PARAMETER_SKIP_PREFLIGHT, true);
            }
            if (preflightCommitmentLevel != null) {
                signAndSendTransactions.put(ProtocolContract.PARAMETER_PREFLIGHT_COMMITMENT,
                        preflightCommitmentLevel.commitmentLevel);
            }
        } catch (JSONException e) {
            throw new UnsupportedOperationException("Failed to create signing payload JSON params", e);
        }

        return new SignAndSendTransactionsFuture(
                methodCall(ProtocolContract.METHOD_SIGN_AND_SEND_TRANSACTIONS,
                        signAndSendTransactions, mClientTimeoutMs),
                transactions.length);
    }

    @NonNull
    public SignAndSendTransactionsFuture signAndSendTransactions(@NonNull String authToken,
                                                                 @NonNull @Size(min = 1) byte[][] transactions,
                                                                 @NonNull CommitmentLevel commitmentLevel)
            throws IOException {
        return signAndSendTransactions(authToken, transactions, commitmentLevel, null, false, null);
    }

    public static class SignAndSendTransactionsFuture
            extends JsonRpc20MethodResultFuture<SignAndSendTransactionsResult>
            implements NotifyOnCompleteFuture<SignAndSendTransactionsResult> {
        @IntRange(from = 1)
        private final int mExpectedNumSignatures;

        private SignAndSendTransactionsFuture(@NonNull NotifyOnCompleteFuture<Object> methodCallFuture,
                                              @IntRange(from = 1) int expectedNumSignatures) {
            super(methodCallFuture);
            mExpectedNumSignatures = expectedNumSignatures;
        }

        @NonNull
        @Override
        protected SignAndSendTransactionsResult processResult(@Nullable Object o)
                throws JsonRpc20InvalidResponseException {
            if (!(o instanceof JSONObject)) {
                throw new JsonRpc20InvalidResponseException("expected result to be a JSON object");
            }
            final JSONObject jo = (JSONObject) o;
            final String[] signatures = unpackResponseStringArray(jo,
                    ProtocolContract.RESULT_SIGNATURES, mExpectedNumSignatures);
            return new SignAndSendTransactionsResult(signatures);
        }

        @Nullable
        @Override
        protected JsonRpc20Exception processRemoteException(@NonNull JsonRpc20RemoteException remoteException) {
            try {
                switch (remoteException.code) {
                    case ProtocolContract.ERROR_INVALID_PAYLOADS:
                        return new InvalidPayloadsException(remoteException.getMessage(),
                                remoteException.data, mExpectedNumSignatures);

                    case ProtocolContract.ERROR_NOT_COMMITTED:
                        return new NotCommittedException(remoteException.getMessage(),
                                remoteException.data, mExpectedNumSignatures);
                }
            } catch (JsonRpc20InvalidResponseException e) {
                return e;
            }
            return null;
        }

        @Override
        public void notifyOnComplete(@NonNull OnCompleteCallback<? super NotifyOnCompleteFuture<SignAndSendTransactionsResult>> cb) {
            mMethodCallFuture.notifyOnComplete((f) -> cb.onComplete(this));
        }
    }

    public static class SignAndSendTransactionsResult {
        @NonNull
        @Size(min = 1)
        public final String[] signatures;

        public SignAndSendTransactionsResult(@NonNull @Size(min = 1) String[] signatures) {
            this.signatures = signatures;
        }

        @NonNull
        @Override
        public String toString() {
            return "SignAndSendTransactionsResult{signatures=" + Arrays.toString(signatures) + '}';
        }
    }

    public static class NotCommittedException extends JsonRpc20RemoteException {
        @NonNull
        @Size(min = 1)
        public final String[] signatures;

        @NonNull
        @Size(min = 1)
        public final boolean[] commitment;

        private NotCommittedException(@NonNull String message,
                                      @Nullable String data,
                                      @IntRange(from = 1) int expectedNumSignatures)
                throws JsonRpc20InvalidResponseException {
            super(ProtocolContract.ERROR_NOT_COMMITTED, message, data);

            if (data == null) {
                throw new JsonRpc20InvalidResponseException("data should not be null");
            }
            final JSONObject o;
            try {
                o = new JSONObject(data);
            } catch (JSONException e) {
                throw new JsonRpc20InvalidResponseException("data is not a valid ERROR_NOT_COMMITTED result");
            }

            signatures = unpackResponseStringArray(o,
                    ProtocolContract.DATA_NOT_COMMITTED_SIGNATURES, expectedNumSignatures);
            commitment = unpackResponseBooleanArray(o,
                    ProtocolContract.DATA_NOT_COMMITTED_COMMITMENT, expectedNumSignatures);
        }

        @NonNull
        @Override
        public String getMessage() {
            return super.getMessage() +
                    "/signatures=" + Arrays.toString(signatures) +
                    "/commitment=" + Arrays.toString(commitment);
        }
    }
}
