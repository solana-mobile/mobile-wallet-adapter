/*
 * Copyright (c) 2022 Solana Labs, Inc.
 */

package com.solana.mobilewalletadapter.clientlib.protocol;

import android.net.Uri;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Size;

import com.solana.mobilewalletadapter.common.ProtocolContract;
import com.solana.mobilewalletadapter.common.protocol.CommitmentLevel;
import com.solana.mobilewalletadapter.common.protocol.PrivilegedMethod;
import com.solana.mobilewalletadapter.common.util.JsonPack;
import com.solana.mobilewalletadapter.common.util.NotifyOnCompleteFuture;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
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

    public static RuntimeException unpackExecutionException(@NonNull ExecutionException e)
            throws JsonRpc20Exception, TimeoutException {
        final Throwable cause = e.getCause();
        if (cause instanceof JsonRpc20Exception) {
            throw (JsonRpc20Exception) cause;
        } else if (cause instanceof TimeoutException) {
            throw (TimeoutException) cause;
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

        return new AuthorizeFuture(methodCall(ProtocolContract.METHOD_AUTHORIZE, authorize, mClientTimeoutMs));
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
    public ReauthorizeFuture reauthorizeAsync(@Nullable Uri identityUri,
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

    @NonNull
    public ReauthorizeResult reauthorize(@Nullable Uri identityUri,
                                         @Nullable Uri iconUri,
                                         @Nullable String identityName,
                                         @NonNull String authToken)
            throws IOException, JsonRpc20Exception, TimeoutException, CancellationException {
        final ReauthorizeFuture future = reauthorizeAsync(identityUri, iconUri, identityName, authToken);
        try {
            return future.get();
        } catch (ExecutionException e) {
            throw unpackExecutionException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while waiting for reauthorize response", e);
        }
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
    public DeauthorizeFuture deauthorizeAsync(@NonNull String authToken)
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

    public void deauthorize(@NonNull String authToken)
            throws IOException, JsonRpc20Exception, TimeoutException, CancellationException {
        final DeauthorizeFuture future = deauthorizeAsync(authToken);
        try {
            future.get();
        } catch (ExecutionException e) {
            throw unpackExecutionException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while waiting for deauthorize response", e);
        }
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
    // sign_* common
    // =============================================================================================

    @NonNull
    private NotifyOnCompleteFuture<Object> signPayloadAsync(@NonNull String method,
                                                            @NonNull String authToken,
                                                            @NonNull @Size(min = 1) byte[][] payloads,
                                                            boolean returnSignedPayloads)
            throws IOException {
        if (authToken.isEmpty()) {
            throw new IllegalArgumentException("authToken cannot be empty");
        }
        for (byte[] p : payloads) {
            if (p == null || p.length == 0) {
                throw new IllegalArgumentException("payloads must be null or empty");
            }
        }

        final JSONArray payloadArr = JsonPack.packByteArraysToBase64PayloadsArray(payloads);
        final JSONObject signPayloads = new JSONObject();
        try {
            signPayloads.put(ProtocolContract.PARAMETER_AUTH_TOKEN, authToken);
            signPayloads.put(ProtocolContract.PARAMETER_PAYLOADS, payloadArr);
            if (returnSignedPayloads) {
                signPayloads.put(ProtocolContract.PARAMETER_RETURN_SIGNED_PAYLOADS, true);
            }
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
        assert(numExpectedPayloads > 0); // checked with inputs to sign*[Async]

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
        assert(numExpectedBooleans > 0); // checked with inputs to sign*[Async]

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
        assert(numExpectedStrings > 0); // checked with inputs to sign*[Async]

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

    public static class SignPayloadResult {
        @NonNull
        @Size(min = 1)
        public final String[] signatures;

        @Nullable
        @Size(min = 1)
        public final byte[][] signedPayloads;

        public SignPayloadResult(@NonNull @Size(min = 1) String[] signatures,
                                 @Nullable @Size(min = 1) byte[][] signedPayloads) {
            this.signatures = signatures;
            this.signedPayloads = signedPayloads;
        }

        @NonNull
        @Override
        public String toString() {
            return "SignPayloadResult{" +
                    "signatures=" + Arrays.toString(signatures) +
                    ", signedPayloads=" + Arrays.toString(signedPayloads) +
                    '}';
        }
    }

    public static class SignPayloadFuture
            extends JsonRpc20MethodResultFuture<SignPayloadResult>
            implements NotifyOnCompleteFuture<SignPayloadResult> {
        @IntRange(from = 1)
        private final int mExpectedNumSignatures;

        private SignPayloadFuture(@NonNull NotifyOnCompleteFuture<Object> methodCallFuture,
                                  @IntRange(from = 1) int expectedNumSignatures) {
            super(methodCallFuture);
            mExpectedNumSignatures = expectedNumSignatures;
        }

        @NonNull
        @Override
        protected SignPayloadResult processResult(@Nullable Object o)
                throws JsonRpc20InvalidResponseException {
            if (!(o instanceof JSONObject)) {
                throw new JsonRpc20InvalidResponseException("expected result to be a JSON object");
            }
            final JSONObject jo = (JSONObject) o;
            final String[] signatures = unpackResponseStringArray(jo,
                    ProtocolContract.RESULT_SIGNATURES, mExpectedNumSignatures);
            final byte[][] signedPayloads;
            if (jo.has(ProtocolContract.RESULT_SIGNED_PAYLOADS)) {
                signedPayloads = unpackResponsePayloadArray(jo,
                        ProtocolContract.RESULT_SIGNED_PAYLOADS, mExpectedNumSignatures);
            } else {
                signedPayloads = null;
            }
            return new SignPayloadResult(signatures, signedPayloads);
        }

        @Nullable
        @Override
        protected JsonRpc20Exception processRemoteException(@NonNull JsonRpc20RemoteException remoteException) {
            if (remoteException.code != ProtocolContract.ERROR_INVALID_PAYLOAD) {
                return null;
            }
            try {
                return new InvalidPayloadException(remoteException.getMessage(),
                        remoteException.data, mExpectedNumSignatures);
            } catch (JsonRpc20InvalidResponseException e) {
                return e;
            }
        }

        @Override
        public void notifyOnComplete(@NonNull OnCompleteCallback<? super NotifyOnCompleteFuture<SignPayloadResult>> cb) {
            mMethodCallFuture.notifyOnComplete((f) -> cb.onComplete(this));
        }
    }

    public static class InvalidPayloadException extends JsonRpc20RemoteException {
        @NonNull
        @Size(min = 1)
        public final boolean[] validPayloads;

        private InvalidPayloadException(@NonNull String message,
                                        @Nullable String data,
                                        @IntRange(from = 1) int expectedNumSignatures)
                throws JsonRpc20InvalidResponseException {
            super(ProtocolContract.ERROR_INVALID_PAYLOAD, message, data);

            if (data == null) {
                throw new JsonRpc20InvalidResponseException("data should not be null");
            }
            final JSONObject o;
            try {
                o = new JSONObject(data);
            } catch (JSONException e) {
                throw new JsonRpc20InvalidResponseException("data is not a valid ERROR_INVALID_PAYLOAD result");
            }
            validPayloads = unpackResponseBooleanArray(o,
                    ProtocolContract.DATA_INVALID_PAYLOAD_VALID, expectedNumSignatures);
        }

        @NonNull
        @Override
        public String getMessage() {
            return super.getMessage() + "/validPayloads=" + Arrays.toString(validPayloads);
        }
    }

    // =============================================================================================
    // sign_transaction
    // =============================================================================================

    @NonNull
    public SignPayloadFuture signTransactionAsync(@NonNull String authToken,
                                                  @NonNull @Size(min = 1) byte[][] transactions,
                                                  boolean returnSignedTransactions)
            throws IOException {
        return new SignPayloadFuture(
                signPayloadAsync(ProtocolContract.METHOD_SIGN_TRANSACTION, authToken, transactions, returnSignedTransactions),
                transactions.length);
    }

    @NonNull
    public SignPayloadResult signTransaction(@NonNull String authToken,
                                             @NonNull @Size(min = 1) byte[][] transactions,
                                             boolean returnSignedTransactions)
            throws IOException, JsonRpc20Exception, TimeoutException, CancellationException {
        final SignPayloadFuture future = signTransactionAsync(authToken, transactions, returnSignedTransactions);
        try {
            return future.get();
        } catch (ExecutionException e) {
            throw unpackExecutionException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while waiting for sign_transaction response", e);
        }
    }

    // =============================================================================================
    // sign_message
    // =============================================================================================

    @NonNull
    public SignPayloadFuture signMessageAsync(@NonNull String authToken,
                                              @NonNull @Size(min = 1) byte[][] messages,
                                              boolean returnSignedMessages)
            throws IOException {
        return new SignPayloadFuture(
                signPayloadAsync(ProtocolContract.METHOD_SIGN_MESSAGE, authToken, messages, returnSignedMessages),
                messages.length);
    }

    @NonNull
    public SignPayloadResult signMessage(@NonNull String authToken,
                                         @NonNull @Size(min = 1) byte[][] messages,
                                         boolean returnSignedMessages)
            throws IOException, JsonRpc20Exception, TimeoutException, CancellationException {
        final SignPayloadFuture future = signMessageAsync(authToken, messages, returnSignedMessages);
        try {
            return future.get();
        } catch (ExecutionException e) {
            throw unpackExecutionException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while waiting for sign_message response", e);
        }
    }

    // =============================================================================================
    // sign_and_send_transaction
    // =============================================================================================

    @NonNull
    public SignAndSendTransactionFuture signAndSendTransactionAsync(@NonNull String authToken,
                                                                    @NonNull @Size(min = 1) byte[][] transactions,
                                                                    @NonNull CommitmentLevel commitmentLevel)
            throws IOException {
        if (authToken.isEmpty()) {
            throw new IllegalArgumentException("authToken cannot be empty");
        }
        for (byte[] t : transactions) {
            if (t == null || t.length == 0) {
                throw new IllegalArgumentException("transactions must be null or empty");
            }
        }

        final JSONArray payloadArr = JsonPack.packByteArraysToBase64PayloadsArray(transactions);
        final JSONObject signAndSendTransaction = new JSONObject();
        try {
            signAndSendTransaction.put(ProtocolContract.PARAMETER_AUTH_TOKEN, authToken);
            signAndSendTransaction.put(ProtocolContract.PARAMETER_PAYLOADS, payloadArr);
            signAndSendTransaction.put(ProtocolContract.PARAMETER_COMMITMENT,
                    commitmentLevel.commitmentLevel);
        } catch (JSONException e) {
            throw new UnsupportedOperationException("Failed to create signing payload JSON params", e);
        }

        return new SignAndSendTransactionFuture(
                methodCall(ProtocolContract.METHOD_SIGN_AND_SEND_TRANSACTION,
                        signAndSendTransaction, mClientTimeoutMs),
                transactions.length);
    }

    @NonNull
    public SignAndSendTransactionResult signAndSendTransaction(@NonNull String authToken,
                                                               @NonNull @Size(min = 1) byte[][] transactions,
                                                               @NonNull CommitmentLevel commitmentLevel)
            throws IOException, JsonRpc20Exception, TimeoutException, CancellationException {
        final SignAndSendTransactionFuture future =
                signAndSendTransactionAsync(authToken, transactions, commitmentLevel);
        try {
            return future.get();
        } catch (ExecutionException e) {
            throw unpackExecutionException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while waiting for sign_and_send_transaction response", e);
        }
    }

    public static class SignAndSendTransactionFuture
            extends JsonRpc20MethodResultFuture<SignAndSendTransactionResult>
            implements NotifyOnCompleteFuture<SignAndSendTransactionResult> {
        @IntRange(from = 1)
        private final int mExpectedNumSignatures;

        private SignAndSendTransactionFuture(@NonNull NotifyOnCompleteFuture<Object> methodCallFuture,
                                             @IntRange(from = 1) int expectedNumSignatures) {
            super(methodCallFuture);
            mExpectedNumSignatures = expectedNumSignatures;
        }

        @NonNull
        @Override
        protected SignAndSendTransactionResult processResult(@Nullable Object o)
                throws JsonRpc20InvalidResponseException {
            if (!(o instanceof JSONObject)) {
                throw new JsonRpc20InvalidResponseException("expected result to be a JSON object");
            }
            final JSONObject jo = (JSONObject) o;
            final String[] signatures = unpackResponseStringArray(jo,
                    ProtocolContract.RESULT_SIGNATURES, mExpectedNumSignatures);
            return new SignAndSendTransactionResult(signatures);
        }

        @Nullable
        @Override
        protected JsonRpc20Exception processRemoteException(@NonNull JsonRpc20RemoteException remoteException) {
            try {
                switch (remoteException.code) {
                    case ProtocolContract.ERROR_INVALID_PAYLOAD:
                        return new InvalidPayloadException(remoteException.getMessage(),
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
        public void notifyOnComplete(@NonNull OnCompleteCallback<? super NotifyOnCompleteFuture<SignAndSendTransactionResult>> cb) {
            mMethodCallFuture.notifyOnComplete((f) -> cb.onComplete(this));
        }
    }

    public static class SignAndSendTransactionResult {
        @NonNull
        @Size(min = 1)
        public final String[] signatures;

        public SignAndSendTransactionResult(@NonNull @Size(min = 1) String[] signatures) {
            this.signatures = signatures;
        }

        @NonNull
        @Override
        public String toString() {
            return "SignAndSendTransactionResult{signatures=" + Arrays.toString(signatures) + '}';
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
