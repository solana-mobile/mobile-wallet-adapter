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

export enum MobileWalletAdapterServiceRequestEventType {
    SignTransactions = 'SIGN_TRANSACTIONS',
    SignMessages = 'SIGN_MESSAGES',
    SignAndSendTransactions = 'SIGN_AND_SEND_TRANSACTIONS',
    AuthorizeDapp = 'AUTHORIZE_DAPP',
    ReauthorizeDapp = 'REAUTHORIZE_DAPP',
    None = 'NONE',
}

// Requests that come from the dApp for Authorization, Signing, Sending services.
export abstract class MobileWalletAdapterServiceRequest {
    type: MobileWalletAdapterServiceRequestEventType = MobileWalletAdapterServiceRequestEventType.None;
    sessionId!: string;
    requestId!: string;

    constructor(sessionId: string, requestId: string) {
        this.sessionId = sessionId
        this.requestId = requestId
    }

    cancel() {
        SolanaMobileWalletAdapterWalletLib.cancelRequest();
    }

    abstract completeWithDecline(): void;
}

type AppIdentity = Readonly<{
    identityUri?: string;
    iconRelativeUri?: string;
    identityName?: string;
}>;

export abstract class VerifiableIdentityRequest extends MobileWalletAdapterServiceRequest {
    cluster: string;
    authorizationScope: Uint8Array;
    appIdentity?: AppIdentity;

    constructor(sessionId: string, requestId: string, cluster: string, authorizationScope: Uint8Array, appIdentity?: AppIdentity) {
        super(sessionId, requestId);
        this.cluster = cluster;
        this.authorizationScope = authorizationScope;
        this.appIdentity = appIdentity;
    }
}

export class AuthorizeDappRequest extends MobileWalletAdapterServiceRequest {
    type: MobileWalletAdapterServiceRequestEventType = MobileWalletAdapterServiceRequestEventType.AuthorizeDapp;
    cluster: string;
    appIdentity?: AppIdentity;

    constructor(sessionId: string, requestId: string, cluster: string, appIdentity?: AppIdentity) {
        super(sessionId, requestId);
        this.cluster = cluster;
        this.appIdentity = appIdentity;
    }

    completeWithAuthorize(
        publicKey: Uint8Array,
        accountLabel: string | null,
        walletUriBase: string | null,
        authorizationScope: Uint8Array | null,
    ): void {
        console.log(this.type + ': completeWithAuthorize: authorized public key =', publicKey);
        const bridgedTypePublicKey: number[] = Array.from(publicKey);
        SolanaMobileWalletAdapterWalletLib.completeWithAuthorize(
            this.sessionId,
            this.requestId,
            bridgedTypePublicKey,
            accountLabel,
            walletUriBase,
            authorizationScope,
        );
    }

    completeWithDecline(): void {
        console.log(this.type + ': completeAuthorizeWithDecline');
        SolanaMobileWalletAdapterWalletLib.completeAuthorizeWithDecline(this.sessionId, this.requestId);
    }
}

export class ReauthorizeDappRequest extends VerifiableIdentityRequest {
    type: MobileWalletAdapterServiceRequestEventType = MobileWalletAdapterServiceRequestEventType.ReauthorizeDapp;

    // TODO: implement ReauthorizeDappRequest
    completeWithDecline(): void {
        console.log(this.type + ': completeAuthorizeWithDecline');
    }
}

export abstract class SignPayloadsRequest extends VerifiableIdentityRequest {
    abstract type: MobileWalletAdapterServiceRequestEventType;
    payloads: Uint8Array[];

    constructor(sessionId: string, requestId: string, payloads: Uint8Array[], cluster: string, authorizationScope: Uint8Array, appIdentity?: AppIdentity) {
        super(sessionId, requestId, cluster, authorizationScope, appIdentity);
        this.payloads = payloads;
    }

    completeWithSignedPayloads(signedPayloads: Uint8Array[]): void {
        console.log(this.type + ': completeSignPayloadsRequest: signedPayloads =', signedPayloads);
        const bridgeTypedPayloads: number[][] = signedPayloads.map((byteArray) => {
            return Array.from(byteArray);
        });
        SolanaMobileWalletAdapterWalletLib.completeWithSignedPayloads(this.sessionId, this.requestId, bridgeTypedPayloads);
    }

    completeWithInvalidPayloads(validArray: boolean[]): void {
        console.log(this.type + ': completeWithInvalidPayloads: validArray =', validArray);
        SolanaMobileWalletAdapterWalletLib.completeWithInvalidPayloads(this.sessionId, this.requestId, validArray);
    }

    completeWithDecline(): void {
        console.log(this.type + ': completeSignPayloadsWithDecline');
        SolanaMobileWalletAdapterWalletLib.completeSignPayloadsWithDecline(this.sessionId, this.requestId);
    }

    completeSignPayloadsWithTooManyPayloads(): void {
        console.log(this.type + ': completeWithTooManyPayloads');
        SolanaMobileWalletAdapterWalletLib.completeSignPayloadsWithTooManyPayloads(this.sessionId, this.requestId);
    }

    completeSignPayloadsWithAuthorizationNotValid(): void {
        console.log(this.type + ': completeSignPayloadsWithAuthorizationNotValid');
        SolanaMobileWalletAdapterWalletLib.completeSignPayloadsWithAuthorizationNotValid(this.sessionId, this.requestId);
    }
}

export class SignMessagesRequest extends SignPayloadsRequest {
    type: MobileWalletAdapterServiceRequestEventType = MobileWalletAdapterServiceRequestEventType.SignMessages;
}

export class SignTransactionsRequest extends SignPayloadsRequest {
    type: MobileWalletAdapterServiceRequestEventType = MobileWalletAdapterServiceRequestEventType.SignTransactions;
}

export class SignAndSendTransactionsRequest extends VerifiableIdentityRequest {
    type: MobileWalletAdapterServiceRequestEventType =
        MobileWalletAdapterServiceRequestEventType.SignAndSendTransactions;
    payloads: Uint8Array[];
    minContextSlot?: string;

    constructor(
        sessionId: string, 
        requestId: string, 
        payloads: Uint8Array[],
        cluster: string,
        authorizationScope: Uint8Array,
        appIdentity?: AppIdentity,
        minContextSlot?: string,
    ) {
        super(sessionId, requestId, cluster, authorizationScope, appIdentity);
        this.payloads = payloads;
        this.minContextSlot = minContextSlot;
    }

    completeWithSignatures(signatures: Uint8Array[]): void {
        console.log(this.type + ': completeWithSignatures: signatures =', signatures);
        const bridgeTypedsignatures: number[][] = signatures.map((byteArray) => {
            return Array.from(byteArray);
        });
        SolanaMobileWalletAdapterWalletLib.completeWithSignatures(this.sessionId, this.requestId, bridgeTypedsignatures);
    }

    completeWithInvalidSignatures(validArray: boolean[]): void {
        console.log(this.type + ': completeWithInvalidSignatures: validArray =', validArray);
        SolanaMobileWalletAdapterWalletLib.completeWithInvalidSignatures(this.sessionId, this.requestId, validArray);
    }

    completeWithDecline(): void {
        console.log(this.type + ': completeSignAndSendWithDecline');
        SolanaMobileWalletAdapterWalletLib.completeSignAndSendWithDecline(this.sessionId, this.requestId);
    }

    completeSignAndSendWithTooManyPayloads(): void {
        console.log(this.type + ': completeSignAndSendWithTooManyPayloads');
        SolanaMobileWalletAdapterWalletLib.completeSignAndSendWithTooManyPayloads(this.sessionId, this.requestId);
    }

    completeSignAndSendWithAuthorizationNotValid(): void {
        console.log(this.type + ': completeSignAndSendWithAuthorizationNotValid');
        SolanaMobileWalletAdapterWalletLib.completeSignAndSendWithAuthorizationNotValid(this.sessionId, this.requestId);
    }
}
