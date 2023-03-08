/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.clientlib.protocol;

import android.annotation.SuppressLint;
import android.net.Uri;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Size;

import com.solana.mobilewalletadapter.clientlib.transaction.TransactionVersion;
import com.solana.mobilewalletadapter.common.ProtocolContract;
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
    // TODO: this assumes Solana-length signatures. Revisit this assumption when adding support for
    // alternative chains.
    private static final int OFFCHAIN_MESSAGE_SIGNATURE_LENGTH = 64;

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
    // authorize/reauthorize shared types
    // =============================================================================================

    public static class AuthorizationResult {
        @NonNull
        public final String authToken;
        @NonNull
        public final byte[] publicKey;
        @Nullable
        public final String accountLabel;
        @Nullable
        public final Uri walletUriBase;

        private AuthorizationResult(@NonNull String authToken,
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
            return "AuthorizationResult{" +
                    "authToken=<REDACTED>" +
                    ", publicKey=" + Arrays.toString(publicKey) +
                    ", accountLabel='" + accountLabel + '\'' +
                    ", walletUriBase=" + walletUriBase +
                    '}';
        }
    }

    public static class AuthorizationFuture
            extends JsonRpc20MethodResultFuture<AuthorizationResult>
            implements NotifyOnCompleteFuture<AuthorizationResult> {
        private AuthorizationFuture(@NonNull NotifyOnCompleteFuture<Object> methodCallFuture) {
            super(methodCallFuture);
        }

        @NonNull
        @Override
        protected AuthorizationResult processResult(@Nullable Object o)
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

            final byte[] publicKey;
            final String accountLabel;
            try {
                final JSONArray accounts = jo.getJSONArray(ProtocolContract.RESULT_ACCOUNTS);
                final JSONObject account = accounts.getJSONObject(0); // TODO(#44): support multiple addresses
                final String b64EncodedAddress = account.getString(ProtocolContract.RESULT_ACCOUNTS_ADDRESS);
                publicKey = JsonPack.unpackBase64PayloadToByteArray(b64EncodedAddress);
                if (account.has(ProtocolContract.RESULT_ACCOUNTS_LABEL)) {
                    accountLabel = account.getString(ProtocolContract.RESULT_ACCOUNTS_LABEL);
                } else {
                    accountLabel = null;
                }
            } catch (JSONException e) {
                throw new JsonRpc20InvalidResponseException("expected one or more addresses");
            }

            final String walletUriBaseStr = jo.has(ProtocolContract.RESULT_WALLET_URI_BASE) ?
                    jo.optString(ProtocolContract.RESULT_WALLET_URI_BASE) : null;
            final Uri walletUriBase = walletUriBaseStr != null ? Uri.parse(walletUriBaseStr) : null;
            if (walletUriBase != null && !"https".equalsIgnoreCase(walletUriBase.getScheme())) {
                throw new InsecureWalletEndpointUriException("Invalid wallet URI prefix '" +
                        walletUriBaseStr + "'; expected a 'https' URI");
            }

            return new AuthorizationResult(authToken, publicKey, accountLabel, walletUriBase);
        }

        @Override
        public void notifyOnComplete(@NonNull OnCompleteCallback<? super NotifyOnCompleteFuture<AuthorizationResult>> cb) {
            mMethodCallFuture.notifyOnComplete((f) -> cb.onComplete(this));
        }
    }

    public static class InsecureWalletEndpointUriException extends JsonRpc20InvalidResponseException {
        /*package*/ InsecureWalletEndpointUriException(@NonNull String message) { super(message); }
    }

    // =============================================================================================
    // authorize
    // =============================================================================================

    @NonNull
    public AuthorizationFuture authorize(@Nullable Uri identityUri,
                                         @Nullable Uri iconUri,
                                         @Nullable String identityName,
                                         @Nullable String cluster)
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
            authorize.put(ProtocolContract.PARAMETER_CLUSTER, cluster); // null is OK
        } catch (JSONException e) {
            throw new UnsupportedOperationException("Failed to create authorize JSON params", e);
        }

        return new AuthorizationFuture(methodCall(ProtocolContract.METHOD_AUTHORIZE, authorize, mClientTimeoutMs));
    }

    // =============================================================================================
    // reauthorize
    // =============================================================================================

    @NonNull
    public AuthorizationFuture reauthorize(@Nullable Uri identityUri,
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

        return new AuthorizationFuture(methodCall(ProtocolContract.METHOD_REAUTHORIZE, reauthorize, mClientTimeoutMs));
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

        @NonNull
        @Size(min = 1)
        public final Object[] supportedTransactionVersions;

        private GetCapabilitiesResult(boolean supportsCloneAuthorization,
                                      boolean supportsSignAndSendTransactions,
                                      @IntRange(from = 0) int maxTransactionsPerSigningRequest,
                                      @IntRange(from = 0) int maxMessagesPerSigningRequest,
                                      @NonNull @Size(min = 1) Object[] supportedTransactionVersions) {
            this.supportsCloneAuthorization = supportsCloneAuthorization;
            this.supportsSignAndSendTransactions = supportsSignAndSendTransactions;
            this.maxTransactionsPerSigningRequest = maxTransactionsPerSigningRequest;
            this.maxMessagesPerSigningRequest = maxMessagesPerSigningRequest;
            this.supportedTransactionVersions = supportedTransactionVersions;
        }

        @NonNull
        @Override
        public String toString() {
            return "GetCapabilitiesResult{" +
                    "supportsCloneAuthorization=" + supportsCloneAuthorization +
                    ", supportsSignAndSendTransactions=" + supportsSignAndSendTransactions +
                    ", maxTransactionsPerSigningRequest=" + maxTransactionsPerSigningRequest +
                    ", maxMessagesPerSigningRequest=" + maxMessagesPerSigningRequest +
                    ", supportedTransactionVersions=" + Arrays.toString(supportedTransactionVersions) +
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
            final Object[] supportedTransactionVersions;
            try {
                supportsCloneAuthorization = jo.getBoolean(ProtocolContract.RESULT_SUPPORTS_CLONE_AUTHORIZATION);
                supportsSignAndSendTransactions = jo.getBoolean(ProtocolContract.RESULT_SUPPORTS_SIGN_AND_SEND_TRANSACTIONS);
                maxTransactionsPerSigningRequest = jo.optInt(ProtocolContract.RESULT_MAX_TRANSACTIONS_PER_REQUEST, 0);
                maxMessagesPerSigningRequest = jo.optInt(ProtocolContract.RESULT_MAX_MESSAGES_PER_REQUEST, 0);

                final JSONArray supportedTransactionVersionsArr = jo.optJSONArray(ProtocolContract.RESULT_SUPPORTED_TRANSACTION_VERSIONS);
                if (supportedTransactionVersionsArr != null) {
                    final int length = supportedTransactionVersionsArr.length();
                    supportedTransactionVersions = new Object[length];
                    for (int i = 0; i < length; i++) {
                        final Object stv = supportedTransactionVersionsArr.get(i);
                        if (stv == JSONObject.NULL || stv instanceof JSONObject || stv instanceof JSONArray) {
                            throw new JSONException("supported_transaction_versions expected to contain only non-null primitive datatypes");
                        }
                        supportedTransactionVersions[i] = stv;
                    }
                } else {
                    // A pre-release version of the Mobile Wallet Adapter protocol spec did not mandate
                    // the supported_transaction_versions field. As such, there may be wallet apps in
                    // production that do not send this field. If not present, assume only legacy
                    // transactions are supported.
                    supportedTransactionVersions = new Object[] { TransactionVersion.LEGACY };
                }
            } catch (JSONException e) {
                throw new JsonRpc20InvalidResponseException("result does not conform to expected format");
            }

            return new GetCapabilitiesResult(supportsCloneAuthorization,
                    supportsSignAndSendTransactions,
                    maxTransactionsPerSigningRequest,
                    maxMessagesPerSigningRequest,
                    supportedTransactionVersions);
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
    @Size(min = 1)
    private static byte[][] unpackResponsePayloadArray(@NonNull JSONObject jo,
                                                       @NonNull String paramName,
                                                       @IntRange(from = 1) int numExpectedPayloads,
                                                       boolean allowNulls)
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
            payloads = JsonPack.unpackBase64PayloadsArrayToByteArrays(arr, allowNulls);
        } catch (JSONException e) {
            throw new JsonRpc20InvalidResponseException(paramName + " must be an array of base64url-encoded Strings");
        } catch (IllegalArgumentException e) {
            throw new JsonRpc20InvalidResponseException(paramName + " does not allow null entries");
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
                        ProtocolContract.RESULT_SIGNED_PAYLOADS, mExpectedNumSignedPayloads, false);
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
    public SignPayloadsFuture signTransactions(@NonNull @Size(min = 1) byte[][] transactions)
            throws IOException {
        for (byte[] t : transactions) {
            if (t == null || t.length == 0) {
                throw new IllegalArgumentException("transactions must not be null or empty");
            }
        }

        final JSONArray payloadsArr = JsonPack.packByteArraysToBase64PayloadsArray(transactions);
        final JSONObject signPayloads = new JSONObject();
        try {
            signPayloads.put(ProtocolContract.PARAMETER_PAYLOADS, payloadsArr);
        } catch (JSONException e) {
            throw new UnsupportedOperationException("Failed to create signing payload JSON params", e);
        }

        return new SignPayloadsFuture(
                methodCall(ProtocolContract.METHOD_SIGN_TRANSACTIONS, signPayloads, mClientTimeoutMs),
                transactions.length);
    }

    // =============================================================================================
    // sign_messages
    // =============================================================================================

    /**
     * @deprecated Consumers of {@link #signMessages(byte[][], byte[][])} should migrate to
     *             {@link #signMessagesDetached(byte[][], byte[][])}, which offers an improved
     *             return type, separating the message from the signatures
     */
    @NonNull
    @Deprecated
    public SignPayloadsFuture signMessages(@NonNull @Size(min = 1) byte[][] messages,
                                           @NonNull @Size(min = 1) byte[][] addresses)
            throws IOException {
        for (byte[] m : messages) {
            if (m == null || m.length == 0) {
                throw new IllegalArgumentException("messages must not be null or empty");
            }
        }

        final JSONArray payloadsArr = JsonPack.packByteArraysToBase64PayloadsArray(messages);
        final JSONArray addressesArr = JsonPack.packByteArraysToBase64PayloadsArray(addresses);
        final JSONObject signPayloads = new JSONObject();
        try {
            signPayloads.put(ProtocolContract.PARAMETER_PAYLOADS, payloadsArr);
            signPayloads.put(ProtocolContract.PARAMETER_ADDRESSES, addressesArr);
        } catch (JSONException e) {
            throw new UnsupportedOperationException("Failed to create signing payload JSON params", e);
        }

        return new SignPayloadsFuture(
                methodCall(ProtocolContract.METHOD_SIGN_MESSAGES, signPayloads, mClientTimeoutMs),
                messages.length);
    }

    @NonNull
    public SignMessagesFuture signMessagesDetached(@NonNull @Size(min = 1) byte[][] messages,
                                                   @NonNull @Size(min = 1) byte[][] addresses)
            throws IOException {
        for (byte[] m : messages) {
            if (m == null || m.length == 0) {
                throw new IllegalArgumentException("messages must not be null or empty");
            }
        }

        final JSONArray payloadsArr = JsonPack.packByteArraysToBase64PayloadsArray(messages);
        final JSONArray addressesArr = JsonPack.packByteArraysToBase64PayloadsArray(addresses);
        final JSONObject signPayloads = new JSONObject();
        try {
            signPayloads.put(ProtocolContract.PARAMETER_PAYLOADS, payloadsArr);
            signPayloads.put(ProtocolContract.PARAMETER_ADDRESSES, addressesArr);
        } catch (JSONException e) {
            throw new UnsupportedOperationException("Failed to create signing payload JSON params", e);
        }

        return new SignMessagesFuture(
                methodCall(ProtocolContract.METHOD_SIGN_MESSAGES, signPayloads, mClientTimeoutMs),
                messages, addresses);
    }

    public static class SignMessagesResult {
        public static class SignedMessage {
            @NonNull
            public final byte[] message;

            @NonNull
            @Size(min = 1)
            public final byte[][] signatures;

            @NonNull
            @Size(min = 1)
            public final byte[][] addresses;

            public SignedMessage(@NonNull byte[] message,
                                 @NonNull byte[][] signatures,
                                 @NonNull byte[][] addresses) {
                this.message = message;
                this.signatures = signatures;
                this.addresses = addresses;
            }

            @NonNull
            @Override
            public String toString() {
                return "SignedMessage{message=" + Arrays.toString(message) +
                        ", signatures=" + Arrays.toString(signatures) + '}';
            }
        }

        @NonNull
        @Size(min = 1)
        public final SignedMessage[] messages;

        public SignMessagesResult(@NonNull @Size(min = 1) SignedMessage[] messages) {
            this.messages = messages;
        }

        @NonNull
        @Override
        public String toString() {
            return "SignMessagesResult{messages=" + Arrays.toString(messages) + '}';
        }
    }

    public static class SignMessagesFuture
            extends JsonRpc20MethodResultFuture<SignMessagesResult>
            implements NotifyOnCompleteFuture<SignMessagesResult> {
        @Size(min = 1)
        private final byte[][] mMessages;

        @Size(min = 1)
        private final byte[][] mAddresses;

        private SignMessagesFuture(@NonNull NotifyOnCompleteFuture<Object> methodCallFuture,
                                   @Size(min = 1) byte[][] messages,
                                   @Size(min = 1) byte[][] addresses) {
            super(methodCallFuture);
            mMessages = messages;
            mAddresses = addresses;
        }

        @NonNull
        @Override
        protected SignMessagesResult processResult(@Nullable Object o)
                throws JsonRpc20InvalidResponseException {
            if (!(o instanceof JSONObject)) {
                throw new JsonRpc20InvalidResponseException("expected result to be a JSON object");
            }
            final JSONObject jo = (JSONObject) o;
            final byte[][] signedPayloads = unpackResponsePayloadArray(jo,
                    ProtocolContract.RESULT_SIGNED_PAYLOADS, mMessages.length, false);

            final SignMessagesResult.SignedMessage[] signedMessages =
                    new SignMessagesResult.SignedMessage[signedPayloads.length];
            final int signaturesLength = OFFCHAIN_MESSAGE_SIGNATURE_LENGTH * mAddresses.length;
            for (int i = 0; i < signedPayloads.length; i++) {
                // Extract signatures from response
                if (signedPayloads[i].length < signaturesLength) {
                    throw new JsonRpc20InvalidResponseException("Payload length too small for all requested signatures");
                }
                final int payloadLength = signedPayloads[i].length - signaturesLength;
                final byte[][] signatures = new byte[mAddresses.length][];
                for (int j = 0; j < signatures.length; j++) {
                    signatures[j] = Arrays.copyOfRange(signedPayloads[i],
                            payloadLength + OFFCHAIN_MESSAGE_SIGNATURE_LENGTH * j,
                            payloadLength + OFFCHAIN_MESSAGE_SIGNATURE_LENGTH * (j + 1));
                }

                // Workaround: some wallets have been observed to only reply with the message signature.
                // This is non-compliant with the spec, but in the interest of maximizing compatibility,
                // detect this case and reuse the original message.
                final byte[] message;
                if (payloadLength == 0) {
                    message = mMessages[i];
                } else {
                    message = Arrays.copyOf(signedPayloads[i], payloadLength);
                }
                signedMessages[i] = new SignMessagesResult.SignedMessage(message, signatures, mAddresses);
            }


            @SuppressLint("Range")
            final SignMessagesResult result = new SignMessagesResult(signedMessages);
            return result;
        }

        @Nullable
        @Override
        protected JsonRpc20Exception processRemoteException(@NonNull JsonRpc20RemoteException remoteException) {
            if (remoteException.code != ProtocolContract.ERROR_INVALID_PAYLOADS) {
                return null;
            }
            try {
                return new InvalidPayloadsException(remoteException.getMessage(),
                        remoteException.data, mMessages.length);
            } catch (JsonRpc20InvalidResponseException e) {
                return e;
            }
        }

        @Override
        public void notifyOnComplete(@NonNull OnCompleteCallback<? super NotifyOnCompleteFuture<SignMessagesResult>> cb) {
            mMethodCallFuture.notifyOnComplete((f) -> cb.onComplete(this));
        }
    }

    // =============================================================================================
    // sign_and_send_transactions
    // =============================================================================================

    @NonNull
    public SignAndSendTransactionsFuture signAndSendTransactions(@NonNull @Size(min = 1) byte[][] transactions,
                                                                 @Nullable Integer minContextSlot)
            throws IOException {
        for (byte[] t : transactions) {
            if (t == null || t.length == 0) {
                throw new IllegalArgumentException("transactions must be null or empty");
            }
        }

        final JSONArray payloadsArr = JsonPack.packByteArraysToBase64PayloadsArray(transactions);
        final JSONObject signAndSendTransactions = new JSONObject();
        try {
            signAndSendTransactions.put(ProtocolContract.PARAMETER_PAYLOADS, payloadsArr);
            if (minContextSlot != null) {
                final JSONObject options = new JSONObject();
                options.put(ProtocolContract.PARAMETER_OPTIONS_MIN_CONTEXT_SLOT, (int)minContextSlot);
                signAndSendTransactions.put(ProtocolContract.PARAMETER_OPTIONS, options);
            }
        } catch (JSONException e) {
            throw new UnsupportedOperationException("Failed to create signing payload JSON params", e);
        }

        return new SignAndSendTransactionsFuture(
                methodCall(ProtocolContract.METHOD_SIGN_AND_SEND_TRANSACTIONS,
                        signAndSendTransactions, mClientTimeoutMs),
                transactions.length);
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
            final byte[][] signatures = unpackResponsePayloadArray(jo,
                    ProtocolContract.RESULT_SIGNATURES, mExpectedNumSignatures, false);
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

                    case ProtocolContract.ERROR_NOT_SUBMITTED:
                        return new NotSubmittedException(remoteException.getMessage(),
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
        public final byte[][] signatures;

        public SignAndSendTransactionsResult(@NonNull @Size(min = 1) byte[][] signatures) {
            this.signatures = signatures;
        }

        @NonNull
        @Override
        public String toString() {
            return "SignAndSendTransactionsResult{signatures=" + Arrays.toString(signatures) + '}';
        }
    }

    public static class NotSubmittedException extends JsonRpc20RemoteException {
        @NonNull
        @Size(min = 1)
        public final byte[][] signatures;

        private NotSubmittedException(@NonNull String message,
                                      @Nullable String data,
                                      @IntRange(from = 1) int expectedNumSignatures)
                throws JsonRpc20InvalidResponseException {
            super(ProtocolContract.ERROR_NOT_SUBMITTED, message, data);

            if (data == null) {
                throw new JsonRpc20InvalidResponseException("data should not be null");
            }
            final JSONObject o;
            try {
                o = new JSONObject(data);
            } catch (JSONException e) {
                throw new JsonRpc20InvalidResponseException("data is not a valid ERROR_NOT_SUBMITTED result");
            }

            signatures = unpackResponsePayloadArray(o,
                    ProtocolContract.DATA_NOT_SUBMITTED_SIGNATURES, expectedNumSignatures, true);
        }

        @NonNull
        @Override
        public String getMessage() {
            return super.getMessage() +
                    "/signatures=" + Arrays.toString(signatures);
        }
    }
}
