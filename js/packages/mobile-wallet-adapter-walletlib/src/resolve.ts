import type { IdentifierArray } from '@wallet-standard/core';
import { NativeModules, Platform } from 'react-native';

const LINKING_ERROR =
    `The package 'solana-mobile-wallet-adapter-walletlib' doesn't seem to be linked. Make sure: \n\n` +
    '- You rebuilt the app after installing the package\n' +
    '- If you are using Lerna workspaces\n' +
    '  - You have added `@solana-mobile/mobile-wallet-adapter-walletlib` as an explicit dependency, and\n' +
    '  - You have added `@solana-mobile/mobile-wallet-adapter-walletlib` to the `nohoist` section of your package.json\n' +
    '- You are not using Expo managed workflow\n';

const SolanaMobileWalletAdapterWalletLib =
    Platform.OS === 'android' && NativeModules.SolanaMobileWalletAdapterWalletLib
        ? NativeModules.SolanaMobileWalletAdapterWalletLib
        : new Proxy(
              {},
              {
                  get() {
                      throw new Error(
                          Platform.OS !== 'android'
                              ? 'The package `solana-mobile-wallet-adapter-walletlib` is only compatible with React Native Android'
                              : LINKING_ERROR,
                      );
                  },
              },
          );

type AppIdentity = Readonly<{
    identityUri?: string;
    iconRelativeUri?: string;
    identityName?: string;
}>;

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
}>;

export type Base64EncodedAddress = string;
export type Base64EncodedSignature = string;
type Base64EncodedSignedMessage = string;

/**
 * Mobile Wallet Adapter Requests are remote requests coming from
 * the dApp for authorization, signing, and sending services.
 */

export type MWARequest =
    | SignMessagesRequest
    | SignTransactionsRequest
    | SignAndSendTransactionsRequest
    | AuthorizeDappRequest
    | ReauthorizeDappRequest
    | DeauthorizeDappRequest;

export enum MWARequestType {
    AuthorizeDappRequest = 'AUTHORIZE_DAPP',
    ReauthorizeDappRequest = 'REAUTHORIZE_DAPP',
    DeauthorizeDappRequest = 'DEAUTHORIZE_DAPP',
    SignMessagesRequest = 'SIGN_MESSAGES',
    SignTransactionsRequest = 'SIGN_TRANSACTIONS',
    SignAndSendTransactionsRequest = 'SIGN_AND_SEND_TRANSACTIONS',
}

interface IMWARequest {
    __type: MWARequestType;
    requestId: string;
    sessionId: string;
}

interface IVerifiableIdentityRequest {
    chain: string;
    authorizationScope: Uint8Array;
    appIdentity?: AppIdentity;
}

export type AuthorizeDappRequest = Readonly<{
    __type: MWARequestType.AuthorizeDappRequest;
    chain: string;
    appIdentity?: AppIdentity;
    features?: IdentifierArray;
    addresses?: [string];
    signInPayload?: SignInPayload;
}> &
    IMWARequest;

export type ReauthorizeDappRequest = Readonly<{
    __type: MWARequestType.ReauthorizeDappRequest;
}> &
    IMWARequest &
    IVerifiableIdentityRequest;

export type DeauthorizeDappRequest = Readonly<{
    __type: MWARequestType.DeauthorizeDappRequest;
}> &
    IMWARequest &
    IVerifiableIdentityRequest;

export type SignMessagesRequest = Readonly<{
    __type: MWARequestType.SignMessagesRequest;
    payloads: Uint8Array[];
}> &
    IMWARequest &
    IVerifiableIdentityRequest;

export type SignTransactionsRequest = Readonly<{
    __type: MWARequestType.SignTransactionsRequest;
    payloads: Uint8Array[];
}> &
    IMWARequest &
    IVerifiableIdentityRequest;

export type SignAndSendTransactionsRequest = Readonly<{
    __type: MWARequestType.SignAndSendTransactionsRequest;
    payloads: Uint8Array[];
    minContextSlot?: number;
    commitment?: string;
    skipPreflight?: boolean;
    maxRetries?: number;
    waitForCommitmentToSendNextTransaction?: boolean;
}> &
    IMWARequest &
    IVerifiableIdentityRequest;

/**
 * MWA Request Responses
 */

export type MWAResponse =
    | AuthorizeDappResponse
    | ReauthorizeDappResponse
    | DeauthorizeDappResponse
    | SignMessagesResponse
    | SignTransactionsResponse
    | SignAndSendTransactionsResponse;

/* Failure Responses */
export enum MWARequestFailReason {
    UserDeclined = 'USER_DECLINED',
    TooManyPayloads = 'TOO_MANY_PAYLOADS',
    InvalidSignatures = 'INVALID_SIGNATURES',
    AuthorizationNotValid = 'AUTHORIZATION_NOT_VALID',
}

export type UserDeclinedResponse = Readonly<{
    failReason: MWARequestFailReason.UserDeclined;
}>;

export type TooManyPayloadsResponse = Readonly<{
    failReason: MWARequestFailReason.TooManyPayloads;
}>;

export type AuthorizationNotValidResponse = Readonly<{
    failReason: MWARequestFailReason.AuthorizationNotValid;
}>;

export type InvalidSignaturesResponse = Readonly<{
    failReason: MWARequestFailReason.InvalidSignatures;
    valid: boolean[];
}>;

/* Authorize Dapp */
export type AuthorizedAccount = Readonly<{
    publicKey: Uint8Array;
    accountLabel?: string;
    icon?: string;
    chains?: IdentifierArray;
    features?: IdentifierArray;
}>;
export type SignInResult = Readonly<{
    address: Base64EncodedAddress;
    signed_message: Base64EncodedSignedMessage;
    signature: Base64EncodedSignature;
    signature_type?: string;
}>;
export type AuthorizeDappCompleteResponse = Readonly<{
    accounts: Array<AuthorizedAccount>;
    walletUriBase?: string;
    authorizationScope?: Uint8Array;
    signInResult?: SignInResult;
}>;
export type AuthorizeDappResponse = AuthorizeDappCompleteResponse | UserDeclinedResponse;

/* Reauthorize Dapp */
export type ReauthorizeDappCompleteResponse = Readonly<{
    authorizationScope?: Uint8Array;
}>;
export type ReauthorizeDappResponse = ReauthorizeDappCompleteResponse | AuthorizationNotValidResponse;

/* Deauthorize Dapp */
export type DeauthorizeDappCompleteResponse = Readonly<{}>;
export type DeauthorizeDappResponse = DeauthorizeDappCompleteResponse | AuthorizationNotValidResponse;

/* Sign Transactions/Messages */
export type SignPayloadsCompleteResponse = Readonly<{ signedPayloads: Uint8Array[] }>;
export type SignPayloadsFailResponse =
    | UserDeclinedResponse
    | TooManyPayloadsResponse
    | AuthorizationNotValidResponse
    | InvalidSignaturesResponse;

export type SignTransactionsResponse = SignPayloadsCompleteResponse | SignPayloadsFailResponse;
export type SignMessagesResponse = SignPayloadsCompleteResponse | SignPayloadsFailResponse;

/* Sign and Send Transaction */
export type SignAndSendTransactionsCompleteResponse = Readonly<{ signedTransactions: Uint8Array[] }>;
export type SignAndSendTransactionsResponse =
    | SignAndSendTransactionsCompleteResponse
    | UserDeclinedResponse
    | TooManyPayloadsResponse
    | AuthorizationNotValidResponse
    | InvalidSignaturesResponse;

export function resolve(request: AuthorizeDappRequest, response: AuthorizeDappResponse): void;
export function resolve(request: ReauthorizeDappRequest, response: ReauthorizeDappResponse): void;
export function resolve(request: DeauthorizeDappRequest, response: DeauthorizeDappResponse): void;
export function resolve(request: SignMessagesRequest, response: SignMessagesResponse): void;
export function resolve(request: SignTransactionsRequest, response: SignTransactionsResponse): void;
export function resolve(request: SignAndSendTransactionsRequest, response: SignAndSendTransactionsResponse): void;
export function resolve(request: MWARequest, response: MWAResponse): void {
    SolanaMobileWalletAdapterWalletLib.resolve(JSON.stringify(request), JSON.stringify(response));
}
