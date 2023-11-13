package com.solana.mobilewalletadapter.common.signin;

public class SignInWithSolanaContract {

    /* RFC 4501 dns authority that is requesting the signing. */
    public static String PAYLOAD_PARAMETER_DOMAIN = "domain"; // type: String

    /* Solana address performing the signing */
    public static String PAYLOAD_PARAMETER_ADDRESS = "address"; // type: String

    /* Human-readable ASCII assertion that the user will sign, and it must not contain newline characters. */
    public static String PAYLOAD_PARAMETER_STATEMENT = "statement"; // type: String

    /* RFC 3986 URI referring to the resource that is the subject of the signing
     *  (as in the __subject__ of a claim). */
    public static String PAYLOAD_PARAMETER_URI = "uri"; // type: String

    /* Current version of the message. */
    public static String PAYLOAD_PARAMETER_VERSION = "version"; // type: String

    /* Chain ID to which the session is bound, and the network where
     * Contract Accounts must be resolved. */
    public static String PAYLOAD_PARAMETER_CHAIN_ID = "chainId"; // type: Number

    /* Randomized token used to prevent replay attacks, at least 8 alphanumeric
     * characters. */
    public static String PAYLOAD_PARAMETER_NONCE = "nonce"; // type: String

    /* ISO 8601 datetime string of the current time. */
    public static String PAYLOAD_PARAMETER_ISSUED_AT = "issuedAt"; // type: String

    /* ISO 8601 datetime string that, if present, indicates when the signed
     * authentication message is no longer valid. */
    public static String PAYLOAD_PARAMETER_EXPIRATION_TIME = "expirationTime"; // type: String

    /* ISO 8601 datetime string that, if present, indicates when the signed
     * authentication message will become valid. */
    public static String PAYLOAD_PARAMETER_NOT_BEFORE = "notBefore"; // type: String

    /* System-specific identifier that may be used to uniquely refer to the
     * sign-in request. */
    public static String PAYLOAD_PARAMETER_REQUEST_ID = "requestId"; // type: String

    /* List of information or references to information the user wishes to have
     * resolved as part of authentication by the relying party. They are
     * expressed as RFC 3986 URIs separated by `\n- `. */
    public static String PAYLOAD_PARAMETER_RESOURCES = "resources"; // type: Array of String

    private SignInWithSolanaContract() {}
}
