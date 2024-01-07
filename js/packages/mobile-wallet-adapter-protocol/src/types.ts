import type { TransactionVersion } from '@solana/web3.js';
import type { SolanaSignInInput } from "@solana/wallet-standard";
import type { IdentifierArray, IdentifierString, WalletAccount, WalletIcon } from '@wallet-standard/core';

export type Account = Readonly<{
    address: Base64EncodedAddress;
    label?: string;
    icon?: WalletIcon;
    chains?: IdentifierArray;
    features?: IdentifierArray;
}> | WalletAccount;

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

export type ProtocolVersion = 'v1' | 'legacy';

export type SessionProperties = Readonly<{
    protocol_version: ProtocolVersion;
}>;

/**
 * The context returned from a wallet after having authorized a given
 * account for use with a given application. You can cache this and
 * use it later to invoke privileged methods.
 */
export type AuthorizationResult = Readonly<{
    accounts: Account[];
    auth_token: AuthToken;
    wallet_uri_base: string;
    sign_in_result?: SignInResult;
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

export type Chain = IdentifierString | Cluster;

export type Finality = 'confirmed' | 'finalized' | 'processed';

export type WalletAssociationConfig = Readonly<{
    baseUri?: string;
}>;

export interface AuthorizeAPI {
    /**
     * @deprecated Replaced by updated authorize() method, which adds MWA 2.0 spec support.
     */
    authorize(params: { cluster: Cluster; identity: AppIdentity }): Promise<AuthorizationResult>;

    authorize(params: { 
        identity: AppIdentity;
        chain?: Chain;
        features?: IdentifierArray; 
        addresses?: string[]; 
        auth_token?: AuthToken; 
        sign_in_payload?: SignInPayload;
    }): Promise<AuthorizationResult>;
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
            max_transactions_per_request: number;
            max_messages_per_request: number;
            supported_transaction_versions: ReadonlyArray<TransactionVersion>;
            features: IdentifierArray;
            /**
             * @deprecated Replaced by features array.
             */
            supports_clone_authorization: boolean;
            /**
             * @deprecated Replaced by features array.
             */
            supports_sign_and_send_transactions: boolean;
        }>
    >;
}
export interface ReauthorizeAPI {
    reauthorize(params: { auth_token: AuthToken; identity: AppIdentity }): Promise<AuthorizationResult>;
}
export interface SignMessagesAPI {
    signMessages(params: {
        addresses: Base64EncodedAddress[];
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
        SignMessagesAPI,
        SignTransactionsAPI,
        SignAndSendTransactionsAPI {}

// optional features
export const SolanaSignTransactions = 'solana:signTransactions'
export const SolanaCloneAuthorization = 'solana:cloneAuthorization'
export const SolanaSignInWithSolana = 'solana:signInWithSolana'

export type SignInPayload = Readonly<{
    domain?: string;
    address?: string;
    statement?: string;
    uri?: string;
    version?: string;
    chainId?: string;
    nonce?: string;
    issuedAt?: string;
    expirationTime?: string;
    notBefore?: string;
    requestId?: string;
    resources?: readonly string[];
}> | SolanaSignInInput;

export type SignInPayloadWithRequiredFields = Partial<SignInPayload> & 
    Required<Pick<SignInPayload, 'domain' | 'address'>>

export type SignInResult = Readonly<{
    address: Base64EncodedAddress;
    signed_message: Base64EncodedSignedMessage;
    signature: Base64EncodedAddress;
    signature_type?: string;
}>;
