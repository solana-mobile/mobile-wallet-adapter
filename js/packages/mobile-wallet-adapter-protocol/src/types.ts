/**
 * Properties that wallets may present to users when an app
 * asks for authorization to execute privileged methods (see
 * {@link PrivilegedMethods}).
 */
export type AppIdentity = Readonly<{
    uri?: string;
    icon?: string;
    name?: string;
}>;

/**
 * An ephemeral elliptic-curve keypair on the P-256 curve.
 * This public key is used to create the association token.
 * The private key is used during session establishment.
 */
export type AssociationKeypair = CryptoKeyPair;

/**
 * The context returned from a wallet after having authorized a given
 * account for use with a given application. You can cache this and
 * use it later to invoke privileged methods.
 */
export type AuthorizationResult = Readonly<{
    auth_token: AuthToken;
    pub_key: string;
    wallet_uri_base: string;
}>;

export type AuthToken = string;

type Base58EncodedSignature = string;

type Base64EncodedMessage = string;

type Base64EncodedSignedMessage = string;

type Base64EncodedSignedTransaction = string;

type Base64EncodedTransaction = string;

export type WalletAssociationConfig = Readonly<{
    baseUri?: string;
}>;

export interface AuthorizeAPI {
    authorize(params: { identity: AppIdentity }): Promise<AuthorizationResult>;
}
export interface CloneAuthorizationAPI {
    cloneAuthorization(params: { auth_token: AuthToken }): Promise<Readonly<{ auth_token: AuthToken }>>;
}
export interface DeauthorizeAPI {
    deauthorize(params: { auth_token: AuthToken }): Promise<Readonly<Record<string, never>>>;
}
export interface ReauthorizeAPI {
    reauthorize(params: { auth_token: AuthToken }): Promise<Readonly<{ auth_token: AuthToken }>>;
}
export interface SignMessagesAPI {
    signMessages(params: {
        payloads: Base64EncodedMessage[];
    }): Promise<Readonly<{ signed_payloads: Base64EncodedSignedMessage[] }>>;
}
export interface SignTransactionsAPI {
    signTransactions(params: {
        payloads: Base64EncodedTransaction[];
    }): Promise<Readonly<{ signed_payloads: Base64EncodedSignedTransaction[] }>>;
}
export interface SignAndSendTransactionsAPI {
    signAndSendTransactions(params: {
        commitment: 'confirmed' | 'finalized' | 'processed';
        payloads: Base64EncodedTransaction[];
    }): Promise<Readonly<{ signatures: Base58EncodedSignature[] }>>;
}

export interface MobileWallet
    extends AuthorizeAPI,
        CloneAuthorizationAPI,
        DeauthorizeAPI,
        ReauthorizeAPI,
        SignMessagesAPI,
        SignTransactionsAPI,
        SignAndSendTransactionsAPI {}
