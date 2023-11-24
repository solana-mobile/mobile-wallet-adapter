import type { TransactionVersion } from '@solana/web3.js';

export type Account = Readonly<{
    address: Base64EncodedAddress;
    label?: string;
}>;

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
    accounts: Account[];
    auth_token: AuthToken;
    wallet_uri_base: string;
}>;

export type AuthToken = string;

export type Base64EncodedAddress = string;

type Base64EncodedSignature = string;

type Base64EncodedMessage = string;

type Base64EncodedSignedMessage = string;

type Base64EncodedSignedTransaction = string;

export type Base64EncodedTransaction = string;

/**
 * @deprecated Replaced by the 'chain' parameter, which adds multi-chain capability as per MWA 2.0 spec.
 */
export type Cluster = 'devnet' | 'testnet' | 'mainnet-beta';

export type Finality = 'confirmed' | 'finalized' | 'processed';

export type Chain = string;

export type Feature = string;

export type WalletAssociationConfig = Readonly<{
    baseUri?: string;
}>;

export interface AuthorizeAPI {
    authorize(params: {
        chain: Chain;
        identity: AppIdentity;
        auth_token?: AuthToken;
        features?: Feature[];
        addresses?: Base64EncodedAddress[];
    }): Promise<AuthorizationResult>;

    /**
     * @deprecated Replaced by updated authorize() method, which adds MWA 2.0 spec support.
     */
    authorize(params: { cluster: Cluster; identity: AppIdentity }): Promise<AuthorizationResult>;
}

export interface CloneAuthorizationAPI {
    cloneAuthorization(params: { auth_token: AuthToken }): Promise<Readonly<{ auth_token: AuthToken }>>;
}
export interface DeauthorizeAPI {
    deauthorize(params: { auth_token: AuthToken }): Promise<Readonly<Record<string, never>>>;
}

export interface GetCapabilitiesAPI {
    getCapabilities(): Promise<
        Readonly<{
            supports_clone_authorization: boolean;
            supports_sign_and_send_transactions: boolean;
            max_transactions_per_request: boolean;
            max_messages_per_request: boolean;
            supported_transaction_versions: ReadonlyArray<TransactionVersion>;
        }>
    >;
}
export interface ReauthorizeAPI {
    reauthorize(params: { auth_token: AuthToken; identity: AppIdentity }): Promise<AuthorizationResult>;
}

/**
 * @deprecated Replaced by signMessagesDetached, which returns the improved MobileWalletAdapterClient.SignMessagesResult type
 */
export interface SignMessagesAPI {
    signMessages(params: {
        addresses: Base64EncodedAddress[];
        payloads: Base64EncodedMessage[];
    }): Promise<Readonly<{ signed_payloads: Base64EncodedSignedMessage[] }>>;
}

export interface SignMessagesDetachedAPI {
    signMessagesDetached(params: { addresses: Base64EncodedAddress[]; messages: Base64EncodedMessage[] }): Promise<
        Readonly<{
            messages: Base64EncodedMessage[];
            signatures: Base64EncodedSignature[][];
            addresses: Base64EncodedAddress[][];
        }>
    >;
}

/**
 * @deprecated signTransactions is deprecated in MWA 2.0, use signAndSendTransactions.
 */
export interface SignTransactionsAPI {
    signTransactions(params: {
        payloads: Base64EncodedTransaction[];
    }): Promise<Readonly<{ signed_payloads: Base64EncodedSignedTransaction[] }>>;
}
export interface SignAndSendTransactionsAPI {
    signAndSendTransactions(params: {
        options?: Readonly<{
            min_context_slot?: number;
            commitment?: string;
            skip_preflight?: boolean;
            max_retries?: number;
            wait_for_commitment_to_send_next_transaction?: boolean;
        }>;
        payloads: Base64EncodedTransaction[];
    }): Promise<Readonly<{ signatures: Base64EncodedSignature[] }>>;
}

export interface MobileWallet
    extends AuthorizeAPI,
        CloneAuthorizationAPI,
        DeauthorizeAPI,
        GetCapabilitiesAPI,
        ReauthorizeAPI,
        SignMessagesDetachedAPI,
        SignMessagesAPI,
        SignTransactionsAPI,
        SignAndSendTransactionsAPI {}
