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

/**
 * Mobile Wallet Adapter Requests are remote requests coming from
 * the dApp for authorization, signing, and sending services.
 */

export type MWARequest =
    | SignMessagesRequest
    | SignTransactionsRequest
    | SignAndSendTransactionsRequest
    | AuthorizeDappRequest;

enum MWARequestType {
    AuthorizeDappRequest,
    ReauthorizeDappRequest,
    DeauthorizeDappRequest,
    SignMessagesRequest,
    SignTransactionsRequest,
    SignAndSendTransactionsRequest,
}
interface IMWARequest {
    __type: MWARequestType;
    requestId: string;
    sessionId: string;
    cluster: string;
    authorizationScope: Uint8Array;
    appIdentity?: AppIdentity;
}

export type AuthorizeDappRequest = Readonly<{
    __type: MWARequestType.AuthorizeDappRequest;
}> &
    IMWARequest;

export type ReauthorizeDappRequest = Readonly<{
    __type: MWARequestType.ReauthorizeDappRequest;
}> &
    IMWARequest;

export type DeauthorizeDappRequest = Readonly<{
    __type: MWARequestType.DeauthorizeDappRequest;
}> &
    IMWARequest;

export type SignMessagesRequest = Readonly<{
    __type: MWARequestType.SignMessagesRequest;
    payloads: Uint8Array[];
}> &
    IMWARequest;

export type SignTransactionsRequest = Readonly<{
    __type: MWARequestType.SignTransactionsRequest;
    payloads: Uint8Array[];
}> &
    IMWARequest;

export type SignAndSendTransactionsRequest = Readonly<{
    __type: MWARequestType.SignAndSendTransactionsRequest;
    payloads: Uint8Array[];
}> &
    IMWARequest;

/**
 * MWA Request Responses
 */

export type MWAResponse =
    | AuthorizeDappResponse
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

export type AuthorizationNotValid = Readonly<{
    failReason: MWARequestFailReason.AuthorizationNotValid;
}>;

export type InvalidSignaturesResponse = Readonly<{
    failReason: MWARequestFailReason.InvalidSignatures;
    valid: boolean[];
}>;

/* Authorize Dapp */
export type AuthorizeDappCompleteResponse = Readonly<{
    publicKey: Uint8Array;
    accountLabel?: string;
    walletUriBase?: string;
    authorizationScope?: Uint8Array;
}>;
export type AuthorizeDappResponse = AuthorizeDappCompleteResponse | UserDeclinedResponse;

/* Sign Transactions/Messages */
export type SignPayloadsCompleteResponse = Readonly<{ signedPayloads: Uint8Array[] }>;
export type SignPayloadsFailResponse =
    | UserDeclinedResponse
    | TooManyPayloadsResponse
    | AuthorizationNotValid
    | InvalidSignaturesResponse;

export type SignTransactionsResponse = SignPayloadsCompleteResponse | SignPayloadsFailResponse;
export type SignMessagesResponse = SignPayloadsCompleteResponse | SignPayloadsFailResponse;

/* Sign and Send Transaction */
export type SignAndSendTransactionsCompleteResponse = Readonly<{ signedTransactions: Uint8Array[] }>;
export type SignAndSendTransactionsResponse =
    | SignAndSendTransactionsCompleteResponse
    | UserDeclinedResponse
    | TooManyPayloadsResponse
    | AuthorizationNotValid
    | InvalidSignaturesResponse;

export function resolve(request: AuthorizeDappRequest, response: AuthorizeDappResponse): void;
export function resolve(request: SignMessagesRequest, response: SignMessagesResponse): void;
export function resolve(request: SignTransactionsRequest, response: SignTransactionsResponse): void;
export function resolve(request: SignAndSendTransactionsRequest, response: SignAndSendTransactionsResponse): void;
export function resolve(request: MWARequest, response: unknown): void {
    switch (request.__type) {
        case MWARequestType.AuthorizeDappRequest:
            // Optionally pre-process the response, which is now nicely typed.
            // Check for conformity?
            SolanaMobileWalletAdapterWalletLib.onResolve(request, response as SignMessagesResponse);
            break;
        case MWARequestType.SignMessagesRequest:
            // Optionally pre-process the response, which is now nicely typed.
            SolanaMobileWalletAdapterWalletLib.onResolve(request, response as SignTransactionsResponse);
            break;
        case MWARequestType.SignTransactionsRequest:
            // Optionally pre-process the response, which is now nicely typed.
            SolanaMobileWalletAdapterWalletLib.onResolve(request, response as SignMessagesResponse);
            break;
        case MWARequestType.SignAndSendTransactionsRequest:
            // Optionally pre-process the response, which is now nicely typed.
            SolanaMobileWalletAdapterWalletLib.onResolve(request, response as SignTransactionsResponse);
            break;
        default:
            console.warn('Unsupported request type');
            break;
    }
}
/**
 * Mobile Wallet Adapter Session Events are notifications and events
 * about the underlying session between the wallet and the dApp.
 */
export enum MWASessionEventType {
    SessionStartEvent,
    SessionReadyEvent,
    SessionTerminatedEvent,
    SessionServingClientsEvent,
    SessionServingCompleteEvent,
    SessionCompleteEvent,
    SessionErrorEvent,
    SessionTeardownCompleteEvent,
    LowPowerNoConnectionEvent,
}
export interface IMWASessionEvent {
    __type: MWASessionEventType;
    sessionId: string;
}
