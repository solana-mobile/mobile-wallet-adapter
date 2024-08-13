package com.solana.mobilewalletadapter.common.signin;

import android.net.Uri;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Size;

import com.solana.mobilewalletadapter.common.datetime.Iso8601DateTime;
import com.solana.mobilewalletadapter.common.util.Base58;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.security.SecureRandom;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SignInWithSolana {

    public static final String HEADER_TYPE = "sip99";

    public static class Payload {
        /* RFC 4501 dns authority that is requesting the signing. */
        @Nullable
        public String domain;

        /* Solana address performing the signing */
        @Nullable
        public byte[] addressRaw;

        /**
         * Solana address performing the signing. @deprecated, use {@link addressRaw} instead.
         * instead.
         */
        @Deprecated(forRemoval = true)
        @Nullable
        public String address;

        /* Human-readable ASCII assertion that the user will sign, and it must not contain newline characters. */
        @Nullable
        public final String statement;

        /* RFC 3986 URI referring to the resource that is the subject of the signing
         *  (as in the __subject__ of a claim). */
        @Nullable
        public final Uri uri;

        /* Current version of the message. */
        @Nullable
        public final String version;

        /* Chain ID to which the session is bound, and the network where
         * Contract Accounts must be resolved. */
        @Nullable
        public final String chainId;

        /* Randomized token used to prevent replay attacks, at least 8 alphanumeric
         * characters. */
        @Nullable
        public final String nonce;

        /* ISO 8601 datetime string of the current time. */
        @Nullable
        public final String issuedAt;

        /* ISO 8601 datetime string that, if present, indicates when the signed
         * authentication message is no longer valid. */
        @Nullable
        public final String expirationTime;

        /* ISO 8601 datetime string that, if present, indicates when the signed
         * authentication message will become valid. */
        @Nullable
        public final String notBefore;

        /* System-specific identifier that may be used to uniquely refer to the
         * sign-in request. */
        @Nullable
        public final String requestId;

        /* List of information or references to information the user wishes to have
         * resolved as part of authentication by the relying party. They are
         * expressed as RFC 3986 URIs separated by `\n- `. */
        @Nullable
        @Size(min = 1)
        public final Uri[] resources;

        public Payload(@Nullable String domain,
                       @Nullable String statement) {
            this(domain, (byte[]) null, statement, null, null, null, null,
                    null, null, null, null, null);
        }

        public Payload(@Nullable String domain,
                       @Nullable byte[] address,
                       @Nullable String statement,
                       @Nullable Uri uri,
                       @Nullable String version,
                       @Nullable String chainId,
                       @Nullable String nonce,
                       @Nullable String issuedAt,
                       @Nullable String expirationTime,
                       @Nullable String notBefore,
                       @Nullable String requestId,
                       @Nullable Uri[] resources) {
            this.domain = domain;
            this.addressRaw = address;
            this.address = address == null ? null : Base58.encode(address);
            this.statement = statement;
            this.uri = uri;
            this.version = version;
            this.chainId = chainId;
            this.requestId = requestId;
            this.resources = resources;

            if (nonce != null) {
                if (nonce.length() < 8 || !nonce.matches("[A-Za-z0-9]+")) {
                    throw new IllegalArgumentException("nonce must be at least 8 alphanumeric characters");
                }
            }
            this.nonce = nonce;

            if (issuedAt != null) {
                try {
                    Iso8601DateTime.parse(issuedAt);
                } catch (ParseException e) {
                    throw new IllegalArgumentException("issuedAt must be a valid ISO 8601 date time string");
                }
            }
            this.issuedAt = issuedAt;

            if (expirationTime != null) {
                try {
                    Iso8601DateTime.parse(expirationTime);
                } catch (ParseException e) {
                    throw new IllegalArgumentException("expirationTime must be a valid ISO 8601 date time string");
                }
            }
            this.expirationTime = expirationTime;

            if (notBefore != null) {
                try {
                    Iso8601DateTime.parse(notBefore);
                } catch (ParseException e) {
                    throw new IllegalArgumentException("notBefore must be a valid ISO 8601 date time string");
                }
            }
            this.notBefore = notBefore;
        }

        @Deprecated(forRemoval = true)
        public Payload(@Nullable String domain,
                       @Nullable String address,
                       @Nullable String statement,
                       @Nullable Uri uri,
                       @Nullable String version,
                       @Nullable String chainId,
                       @Nullable String nonce,
                       @Nullable String issuedAt,
                       @Nullable String expirationTime,
                       @Nullable String notBefore,
                       @Nullable String requestId,
                       @Nullable Uri[] resources) {
            this(domain, (byte[]) null, statement, uri, version, chainId, nonce,
                    issuedAt, expirationTime, notBefore, requestId, resources);

            if (address == null) return;
            try { this.addressRaw = Base58.decode(address); } catch (IllegalArgumentException e) {
                try { this.addressRaw = Base64.decode(address, Base64.DEFAULT); }
                catch (IllegalArgumentException e2) {
                    throw new IllegalArgumentException("Failed to decode address: " + address);
                }
            }
            this.address = address;
        }

        public String prepareMessage(byte[] address) {
            this.addressRaw = address;
            return prepareMessage();
        }

        @Deprecated(forRemoval = true)
        public String prepareMessage(String address) {
            try { this.addressRaw = Base58.decode(address); } catch (IllegalArgumentException e) {
                try { this.addressRaw = Base64.decode(address, Base64.DEFAULT); }
                catch (IllegalArgumentException e2) {
                    throw new IllegalArgumentException("Failed to decode address: " + address);
                }
            }
            this.address = address;
            return prepareMessage();
        }

        public String prepareMessage() {
            if (domain == null) {
                throw new IllegalStateException("cannot prepare sign in message, no domain provided");
            }
            if (addressRaw == null) {
                throw new IllegalStateException("cannot prepare sign in message, no address provided");
            }
            return toV1Message();
        }

        private String toV1Message() {
            final String header = domain + " wants you to sign in with your Solana account:";
            String prefix = String.join("\n", header, Base58.encode(addressRaw));

            final List<String> suffixArray = new ArrayList<>();

            if (uri != null) {
                final String uriField = "URI: " + uri;
                suffixArray.add(uriField);
            }

            if (version != null) {
                final String versionField = "Version: " + version;
                suffixArray.add(versionField);
            }

            if (chainId != null) {
                final String chainField = "Chain ID: " + chainId;
                suffixArray.add(chainField);
            }

            if (nonce != null) {
                final String nonceField = "Nonce: " + nonce;
                suffixArray.add(nonceField);
            }

            if (issuedAt != null) {
                final String issuedAtField = "Issued At: " + issuedAt;
                suffixArray.add(issuedAtField);
            }

            if (expirationTime != null) {
                final String expirationTimeField = "Expiration Time: " + expirationTime;
                suffixArray.add(expirationTimeField);
            }

            if (notBefore != null) {
                final String expirationTime = "Not Before: " + notBefore;
                suffixArray.add(expirationTime);
            }

            if (requestId != null) {
                suffixArray.add("Request ID: " + requestId);
            }

            if (resources != null && resources.length > 0) {
                StringBuilder resourcesBuilder = new StringBuilder("Resources:");
                for (int i  = 0; i < resources.length; i++) {
                    resourcesBuilder.append("\n- ");
                    resourcesBuilder.append(resources[i].toString());
                }
                suffixArray.add(resourcesBuilder.toString());
            }

            final String suffix = String.join("\n", suffixArray);

            if (statement != null) {
                prefix = String.join("\n\n", prefix, statement);
                prefix += "\n";
            } else {
                prefix += "\n\n";
            }

            return suffix.isEmpty() ? prefix.trim() : String.join("\n", prefix, suffix);
        }

        @NonNull
        public static Payload fromMessage(String message) {
            return Parser.parseMessage(message);
        }

        @NonNull
        public static Payload fromJson(JSONObject jsonObject) throws JSONException {

            final String domain = jsonObject.has(SignInWithSolanaContract.PAYLOAD_PARAMETER_DOMAIN)
                    ? jsonObject.optString(SignInWithSolanaContract.PAYLOAD_PARAMETER_DOMAIN) : null;
            final String addressStr = jsonObject.has(SignInWithSolanaContract.PAYLOAD_PARAMETER_ADDRESS)
                    ? jsonObject.optString(SignInWithSolanaContract.PAYLOAD_PARAMETER_ADDRESS) : null;
            final byte[] address = addressStr != null ? Base58.decode(addressStr) : null;

            final String uriString = jsonObject.optString(SignInWithSolanaContract.PAYLOAD_PARAMETER_URI);
            final Uri uri = uriString.isEmpty() ? null : Uri.parse(uriString);

            final String statement = jsonObject.has(SignInWithSolanaContract.PAYLOAD_PARAMETER_STATEMENT)
                    ? jsonObject.optString(SignInWithSolanaContract.PAYLOAD_PARAMETER_STATEMENT): null;
            final String version= jsonObject.has(SignInWithSolanaContract.PAYLOAD_PARAMETER_VERSION)
                    ? jsonObject.optString(SignInWithSolanaContract.PAYLOAD_PARAMETER_VERSION) : null;
            final String chainId = jsonObject.has(SignInWithSolanaContract.PAYLOAD_PARAMETER_CHAIN_ID)
                    ? jsonObject.optString(SignInWithSolanaContract.PAYLOAD_PARAMETER_CHAIN_ID) : null;
            final String nonce = jsonObject.has(SignInWithSolanaContract.PAYLOAD_PARAMETER_NONCE)
                    ? jsonObject.optString(SignInWithSolanaContract.PAYLOAD_PARAMETER_NONCE) : null;
            final String issuedAt = jsonObject.has(SignInWithSolanaContract.PAYLOAD_PARAMETER_ISSUED_AT)
                    ? jsonObject.optString(SignInWithSolanaContract.PAYLOAD_PARAMETER_ISSUED_AT) : null;
            final String expirationTime = jsonObject.has(SignInWithSolanaContract.PAYLOAD_PARAMETER_EXPIRATION_TIME)
                    ? jsonObject.getString(SignInWithSolanaContract.PAYLOAD_PARAMETER_EXPIRATION_TIME): null;
            final String notBefore = jsonObject.has(SignInWithSolanaContract.PAYLOAD_PARAMETER_NOT_BEFORE)
                    ? jsonObject.getString(SignInWithSolanaContract.PAYLOAD_PARAMETER_NOT_BEFORE) : null;
            final String requestId = jsonObject.has(SignInWithSolanaContract.PAYLOAD_PARAMETER_REQUEST_ID)
                    ? jsonObject.getString(SignInWithSolanaContract.PAYLOAD_PARAMETER_REQUEST_ID) : null;

            JSONArray resourcesArr = jsonObject.optJSONArray("resources");
            final Uri[] resources;
            if (resourcesArr != null) {
                resources = new Uri[resourcesArr .length()];
                for (int i = 0; i < resourcesArr .length(); i++) {
                    resources[i] = Uri.parse(resourcesArr.getString(i));
                }
            } else {
                resources = null;
            }

            return new Payload(domain, address, statement, uri, version, chainId, nonce,
                    issuedAt, expirationTime, notBefore, requestId, resources);
        }

        public JSONObject toJson() throws JSONException {
            JSONObject json = new JSONObject();
            json.put(SignInWithSolanaContract.PAYLOAD_PARAMETER_DOMAIN, domain);
            json.put(SignInWithSolanaContract.PAYLOAD_PARAMETER_ADDRESS, 
                    addressRaw != null ? Base58.encode(addressRaw) : null);
            json.put(SignInWithSolanaContract.PAYLOAD_PARAMETER_STATEMENT, statement);
            json.put(SignInWithSolanaContract.PAYLOAD_PARAMETER_URI, uri != null ? uri.toString() : null);
            json.put(SignInWithSolanaContract.PAYLOAD_PARAMETER_VERSION, version);
            json.put(SignInWithSolanaContract.PAYLOAD_PARAMETER_CHAIN_ID, chainId);
            json.put(SignInWithSolanaContract.PAYLOAD_PARAMETER_NONCE, nonce);
            json.put(SignInWithSolanaContract.PAYLOAD_PARAMETER_ISSUED_AT, issuedAt);
            json.put(SignInWithSolanaContract.PAYLOAD_PARAMETER_EXPIRATION_TIME, expirationTime);
            json.put(SignInWithSolanaContract.PAYLOAD_PARAMETER_NOT_BEFORE, notBefore);
            json.put(SignInWithSolanaContract.PAYLOAD_PARAMETER_REQUEST_ID, requestId);
            if (resources != null) {
                JSONArray jsonArray = new JSONArray();
                for (Uri resource : resources) {
                    jsonArray.put(resource.toString());
                }
                json.put(SignInWithSolanaContract.PAYLOAD_PARAMETER_RESOURCES, jsonArray);
            }

            return json;
        }

        private String generateNonce() {
            int min = 10000000;
            int max = Integer.MAX_VALUE;
            int value = new SecureRandom().nextInt(max - min) + min;
            return String.valueOf(value);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Payload payload = (Payload) o;
            return Objects.equals(domain, payload.domain)
                    && Arrays.equals(addressRaw, payload.addressRaw)
                    && Objects.equals(statement, payload.statement)
                    && Objects.equals(uri, payload.uri)
                    && Objects.equals(version, payload.version)
                    && Objects.equals(chainId, payload.chainId)
                    && Objects.equals(nonce, payload.nonce)
                    && Objects.equals(issuedAt, payload.issuedAt)
                    && Objects.equals(expirationTime, payload.expirationTime)
                    && Objects.equals(notBefore, payload.notBefore)
                    && Objects.equals(requestId, payload.requestId)
                    && Arrays.equals(resources, payload.resources);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(domain, statement, uri, version, chainId,
                    nonce, issuedAt, expirationTime, notBefore, requestId);
            result = 31 * result + Arrays.hashCode(addressRaw);
            result = 31 * result + Arrays.hashCode(resources);
            return result;
        }
    }

    static class Parser {
        static final String DOMAIN = "(?<domain>([^?#]*)) wants you to sign in with your Solana account:";
        static final String ADDRESS = "\\n(?<address>[a-zA-Z0-9]{32,44})(?:\\n{1,2}|$)";
        static final String STATEMENT = "((?<statement>[\\S\\s]+?)(?:\\n|$))??";
        static final String URI = "(?:(?:[^:?#]+):)?(?:[^?#\\n]*)?(?:[^?#\\n]*)(?:\\?(?:[^#\\n]*))?(?:#(?:.*))";
        static final String URI_LINE = "(?:\\nURI: (?<uri>" + URI + "?))?";
        static final String VERSION = "(?:\\nVersion: (?<version>1))?";
        static final String CHAIN_ID = "(?:\\nChain ID: (?<chainId>[0-9]+))?";
        static final String NONCE = "(?:\\nNonce: (?<nonce>[a-zA-Z0-9]{8,}))?";
        static final String DATETIME = "(?:[0-9]+)-(?:0[1-9]|1[012])-(?:0[1-9]|[12][0-9]|3[01])[Tt](?:[01][0-9]|2[0-3]):(?:[0-5][0-9]):(?:[0-5][0-9]|60)(?:.[0-9]+)?(?:(?:[Zz])|(?:[+|-](?:[01][0-9]|2[0-3]):[0-5][0-9]))";
        static final String ISSUED_AT = "(?:\\nIssued At: (?<issuedAt>" + DATETIME + "))?";
        static final String EXPIRATION_TIME = "(?:\\nExpiration Time: (?<expirationTime>" + DATETIME + "))?";
        static final String NOT_BEFORE = "(?:\\nNot Before: (?<notBefore>" + DATETIME + "))?";
        static final String REQUEST_ID = "(?:\\nRequest ID: (?<requestId>[-._~!$&'()*+,;=:@%a-zA-Z0-9]*))?";
        static final String RESOURCES = "(?:\\nResources:(?<resources>(\\n- " + URI + "?)+))?";
        static final String MESSAGE = "^" + DOMAIN + ADDRESS + STATEMENT + URI_LINE + VERSION + CHAIN_ID
                + NONCE + ISSUED_AT + EXPIRATION_TIME + NOT_BEFORE + REQUEST_ID + RESOURCES + "$";

        public static final Pattern messagePattern = Pattern.compile(MESSAGE);

        // named groups requires Android API 26, so we have to fall back on group index
        static final int GROUP_DOMAIN = 1;
        static final int GROUP_ADDRESS = 3;
        static final int GROUP_STATEMENT = 5;
        static final int GROUP_URI = 6;
        static final int GROUP_VERSION = 7;
        static final int GROUP_CHAIN_ID = 8;
        static final int GROUP_NONCE = 9;
        static final int GROUP_ISSUED_AT = 10;
        static final int GROUP_EXPIRATION_TIME = 11;
        static final int GROUP_NOT_BEFORE = 12;
        static final int GROUP_REQUEST_ID = 13;
        static final int GROUP_RESOURCES = 14;

        static Payload parseMessage(String message) {
            Matcher payloadMatcher = Parser.messagePattern.matcher(message);
            // named groups requires Android API 26, so we have to fall back on this unfortunate code
            if (payloadMatcher.find()) {
                String domain = payloadMatcher.group(GROUP_DOMAIN);
                if (domain == null) {
                    throw new IllegalArgumentException("Failed to parse message: domain not found");
                }

                String addressStr = payloadMatcher.group(GROUP_ADDRESS);
                if (addressStr == null) {
                    throw new IllegalArgumentException("Failed to parse message: address not found");
                }
                byte [] address = Base58.decode(addressStr);

                String statement = payloadMatcher.group(GROUP_STATEMENT);
                String uriString = payloadMatcher.group(GROUP_URI);
                Uri uri = uriString != null ? Uri.parse(uriString) : null;

                String version = payloadMatcher.group(GROUP_VERSION);
                String chainId = payloadMatcher.group(GROUP_CHAIN_ID);
                String nonce = payloadMatcher.group(GROUP_NONCE);
                String issuedAt = payloadMatcher.group(GROUP_ISSUED_AT);
                String expirationTime = payloadMatcher.group(GROUP_EXPIRATION_TIME);
                String notBefore = payloadMatcher.group(GROUP_NOT_BEFORE);
                String requestId = payloadMatcher.group(GROUP_REQUEST_ID);

                String resourcesString = payloadMatcher.group(GROUP_RESOURCES);
                Uri[] resources;
                if (resourcesString == null){
                    resources = null;
                } else {
                    String[] resourcesSplit = resourcesString.split("\n- ");
                    resources = new Uri[resourcesSplit.length - 1];
                    for (int i = 0; i < resources.length; i++) {
                        resources[i] = Uri.parse(resourcesSplit[i + 1]);
                    }
                }

                return new Payload(domain, address, statement, uri, version, chainId, nonce,
                        issuedAt, expirationTime, notBefore, requestId, resources);
            } else {
                throw new IllegalArgumentException("Input is not a valid SIWS message");
            }
        }

        private Parser() {}
    }

    private SignInWithSolana() {}
}
