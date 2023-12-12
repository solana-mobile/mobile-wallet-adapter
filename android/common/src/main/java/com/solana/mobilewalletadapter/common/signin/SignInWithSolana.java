package com.solana.mobilewalletadapter.common.signin;

import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Size;

import com.solana.mobilewalletadapter.common.datetime.Iso8601DateTime;

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

import kotlin.text.Regex;
import kotlin.text.RegexOption;

public class SignInWithSolana {

    public static final String HEADER_TYPE = "sip99";

    public static class Payload {
        /* RFC 4501 dns authority that is requesting the signing. */
        @NonNull
        public final String domain;

        /* Solana address performing the signing */
        @Nullable
        public String address;

        /* Human-readable ASCII assertion that the user will sign, and it must not contain newline characters. */
        @Nullable
        public final String statement;

        /* RFC 3986 URI referring to the resource that is the subject of the signing
         *  (as in the __subject__ of a claim). */
        @NonNull
        public final Uri uri;

        /* Current version of the message. */
        @NonNull
        public final String version;

        /* Chain ID to which the session is bound, and the network where
         * Contract Accounts must be resolved. */
        public final int chainId;

        /* Randomized token used to prevent replay attacks, at least 8 alphanumeric
         * characters. */
        @NonNull
        public final String nonce;

        /* ISO 8601 datetime string of the current time. */
        @NonNull
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

        public Payload(@NonNull String domain,
                       @Nullable String statement,
                       @NonNull Uri uri,
                       @Nullable String issuedAt) {
            this(domain, null, statement, uri, "1", 1, null,
                    issuedAt, null, null, null, null);
        }

        public Payload(@NonNull String domain,
                       @Nullable String address,
                       @Nullable String statement,
                       @NonNull Uri uri,
                       @NonNull String version,
                       int chainId,
                       @Nullable String nonce,
                       @Nullable String issuedAt,
                       @Nullable String expirationTime,
                       @Nullable String notBefore,
                       @Nullable String requestId,
                       @Nullable Uri[] resources) {
            this.domain = domain;
            this.address = address;
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
                this.nonce = nonce;
            } else {
                this.nonce = generateNonce();
            }

            if (issuedAt != null) {
                try {
                    Iso8601DateTime.parse(issuedAt);
                } catch (ParseException e) {
                    throw new IllegalArgumentException("issuedAt must be a valid ISO 8601 date time string");
                }
                this.issuedAt = issuedAt;
            } else {
                this.issuedAt = Iso8601DateTime.now();
            }

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

        public String prepareMessage(String address) {
            this.address = address;
            return prepareMessage();
        }

        public String prepareMessage() {
            if (address == null) {
                throw new IllegalStateException("cannot prepare sign in message, no address provided");
            }
            switch (version) {
                case "1":
                    return toV1Message();
                default:
                    throw new UnsupportedOperationException("Only version 1 SIWS messages are currently supported");
            }
        }

        private String toV1Message() {
            final String header = domain + " wants you to sign in with your Solana account:";
            String prefix = String.join("\n", header, address);

            final String uriField = "URI: " + uri;
            final String versionField = "Version: " + version;
            final String chainField = "Chain ID: " + chainId;
            final String nonceField = "Nonce: " + nonce;
            final List<String> suffixArray = new ArrayList<>(
                    List.of(uriField, versionField, chainField, nonceField));

            final String issuedAtField = "Issued At: " + issuedAt;
            suffixArray.add(issuedAtField);

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

            return String.join("\n", prefix, suffix);
        }

        @NonNull
        public static Payload fromMessage(String message) {
            return Parser.parseMessage(message);
        }

        @NonNull
        public static Payload fromJson(JSONObject jsonObject) throws JSONException {

            final String domain = jsonObject.optString(SignInWithSolanaContract.PAYLOAD_PARAMETER_DOMAIN);
            if (domain.isEmpty()) throw new IllegalArgumentException("domain is required");

            final String address = jsonObject.has(SignInWithSolanaContract.PAYLOAD_PARAMETER_ADDRESS) ?
                    jsonObject.optString(SignInWithSolanaContract.PAYLOAD_PARAMETER_ADDRESS) : null;

            final String uriString = jsonObject.optString(SignInWithSolanaContract.PAYLOAD_PARAMETER_URI);
            if (uriString.isEmpty()) throw new IllegalArgumentException("uri is required");
            final Uri uri = Uri.parse(uriString);

            final String statement = jsonObject.has(SignInWithSolanaContract.PAYLOAD_PARAMETER_STATEMENT)
                    ? jsonObject.optString(SignInWithSolanaContract.PAYLOAD_PARAMETER_STATEMENT): null;

            final String versionStr = jsonObject.optString(SignInWithSolanaContract.PAYLOAD_PARAMETER_VERSION);
            final String version = !versionStr.isEmpty() ? versionStr : "1";

            final int chainId = jsonObject.getInt(SignInWithSolanaContract.PAYLOAD_PARAMETER_CHAIN_ID);

            final String nonce = jsonObject.optString(SignInWithSolanaContract.PAYLOAD_PARAMETER_NONCE);
            // TODO: should we default? (random 8 char alphanumeric string)

            final String issuedAt = jsonObject.optString(SignInWithSolanaContract.PAYLOAD_PARAMETER_ISSUED_AT);
            // TODO should we default? (now)

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
            json.put(SignInWithSolanaContract.PAYLOAD_PARAMETER_ADDRESS, address);
            json.put(SignInWithSolanaContract.PAYLOAD_PARAMETER_STATEMENT, statement);
            json.put(SignInWithSolanaContract.PAYLOAD_PARAMETER_URI, uri.toString());
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
            return domain.equals(payload.domain)
                    && Objects.equals(address, payload.address)
                    && Objects.equals(statement, payload.statement)
                    && uri.equals(payload.uri)
                    && version.equals(payload.version)
                    && chainId == payload.chainId
                    && nonce.equals(payload.nonce)
                    && issuedAt.equals(payload.issuedAt)
                    && Objects.equals(expirationTime, payload.expirationTime)
                    && Objects.equals(notBefore, payload.notBefore)
                    && Objects.equals(requestId, payload.requestId)
                    && Arrays.equals(resources, payload.resources);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(domain, address, statement, uri, version, chainId,
                    nonce, issuedAt, expirationTime, notBefore, requestId);
            result = 31 * result + Arrays.hashCode(resources);
            return result;
        }
    }

    static class Parser {
        static final String DOMAIN = "(?<domain>([^?#]*)) wants you to sign in with your Solana account:";
        static final String ADDRESS = "\\n(?<address>[a-zA-Z0-9]{32,44})\\n\\n";
        static final String STATEMENT = "((?<statement>[^\\n]+)\\n)?";
        static final String URI = "(([^:?#]+):)?(([^?#]*))?([^?#]*)(\\?([^#]*))?(#(.*))";
        static final String URI_LINE = "\\nURI: (?<uri>" + URI + "?)";
        static final String VERSION = "\\nVersion: (?<version>1)";
        static final String CHAIN_ID = "\\nChain ID: (?<chainId>[0-9]+)";
        static final String NONCE = "\\nNonce: (?<nonce>[a-zA-Z0-9]{8,})";
        static final String DATETIME = "([0-9]+)-(0[1-9]|1[012])-(0[1-9]|[12][0-9]|3[01])[Tt]([01][0-9]|2[0-3]):([0-5][0-9]):([0-5][0-9]|60)(.[0-9]+)?(([Zz])|([+|-]([01][0-9]|2[0-3]):[0-5][0-9]))";
        static final String ISSUED_AT = "\\nIssued At: (?<issuedAt>" + DATETIME + ")";
        static final String EXPIRATION_TIME = "(\\nExpiration Time: (?<expirationTime>" + DATETIME + "))?";
        static final String NOT_BEFORE = "(\\nNot Before: (?<notBefore>" + DATETIME + "))?";
        static final String REQUEST_ID = "(\\nRequest ID: (?<requestId>[-._~!$&'()*+,;=:@%a-zA-Z0-9]*))?";
        static final String RESOURCES = "(\\nResources:(?<resources>(\\n- " + URI + "?)+))?";
        static final String MESSAGE = "^" + DOMAIN + ADDRESS + STATEMENT + URI_LINE + VERSION + CHAIN_ID
                + NONCE + ISSUED_AT + EXPIRATION_TIME + NOT_BEFORE + REQUEST_ID + RESOURCES + "$";

        public static final Pattern messagePattern = Pattern.compile(MESSAGE);

        // named groups requires Android API 26, so we have to fall back on group index
        static final int GROUP_DOMAIN = 1;
        static final int GROUP_ADDRESS = 3;
        static final int GROUP_STATEMENT = 5;
        static final int GROUP_URI = 6;
        static final int GROUP_VERSION = 16;
        static final int GROUP_CHAIN_ID = 17;
        static final int GROUP_NONCE = 18;
        static final int GROUP_ISSUED_AT = 19;
        static final int GROUP_EXPIRATION_TIME = 32;
        static final int GROUP_NOT_BEFORE = 45;
        static final int GROUP_REQUEST_ID = 58;
        static final int GROUP_RESOURCES = 60;

        static Payload parseMessage(String message) {
            Matcher payloadMatcher = Parser.messagePattern.matcher(message);
            // named groups requires Android API 26, so we have to fall back on this unfortunate code
            if (payloadMatcher.find()) {
                String domain = payloadMatcher.group(GROUP_DOMAIN);
                if (domain == null) {
                    throw new IllegalArgumentException("Failed to parse message: domain not found");
                }

                String address = payloadMatcher.group(GROUP_ADDRESS);
                if (address == null) {
                    throw new IllegalArgumentException("Failed to parse message: address not found");
                }

                String statement = payloadMatcher.group(GROUP_STATEMENT);
                String uriString = payloadMatcher.group(GROUP_URI);
                Uri uri = Uri.parse(uriString);

                String version = payloadMatcher.group(GROUP_VERSION);
                if (version == null) {
                    throw new IllegalArgumentException("Failed to parse message: version not found");
                }

                String chainIdString = payloadMatcher.group(GROUP_CHAIN_ID);
                int chainId;
                if (chainIdString == null) {
                    chainId = 1;
                } else {
                    chainId= Integer.parseInt(chainIdString, 10);
                }

                String nonce = payloadMatcher.group(GROUP_NONCE);
                if (nonce == null) {
                    throw new IllegalArgumentException("Failed to parse message: nonce not found");
                }

                String issuedAt = payloadMatcher.group(GROUP_ISSUED_AT);
                if (issuedAt == null) {
                    throw new IllegalArgumentException("Failed to parse message: issuedAt not found");
                }

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
