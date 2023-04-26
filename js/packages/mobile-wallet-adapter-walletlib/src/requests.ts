import { NativeModules, Platform } from "react-native";

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

export type MobileWalletAdapterServiceRequest =
  | NoneRequest
  | SessionTerminatedRequest
  | LowPowerNoConnectionRequest
  | AuthorizeDappRequest
  | ReauthorizeDappRequest
  | SignMessagesRequest
  | SignTransactionsRequest
  | SignAndSendTransactionsRequest;

export enum MobileWalletAdapterServiceEventType {
    SignTransactions = 'SIGN_TRANSACTIONS',
    SignMessages = 'SIGN_MESSAGES',
    SignAndSendTransactions = 'SIGN_AND_SEND_TRANSACTIONS',
    SessionTerminated = 'SESSION_TERMINATED',
    LowPowerNoConnection = 'LOW_POWER_NO_CONNECTION',
    AuthorizeDapp = 'AUTHORIZE_DAPP',
    ReauthorizeDapp = 'REAUTHORIZE_DAPP',
    None = 'NONE'
}

abstract class MobileWalletAdapterServiceRequestBase {
    type: MobileWalletAdapterServiceEventType = MobileWalletAdapterServiceEventType.None;
}

export abstract class MobileWalletAdapterServiceRemoteRequest extends MobileWalletAdapterServiceRequestBase {
    cancel() {
        SolanaMobileWalletAdapterWalletLib.cancelRequest();
    }

    abstract completeWithDecline(): void;
}

export class NoneRequest extends MobileWalletAdapterServiceRequestBase {
    type: MobileWalletAdapterServiceEventType = MobileWalletAdapterServiceEventType.None;
}

export class SessionTerminatedRequest extends MobileWalletAdapterServiceRequestBase {
    type: MobileWalletAdapterServiceEventType = MobileWalletAdapterServiceEventType.SessionTerminated;
}

export class LowPowerNoConnectionRequest extends MobileWalletAdapterServiceRequestBase {
    type: MobileWalletAdapterServiceEventType = MobileWalletAdapterServiceEventType.LowPowerNoConnection;
}

export class AuthorizeDappRequest extends MobileWalletAdapterServiceRemoteRequest {
    type: MobileWalletAdapterServiceEventType = MobileWalletAdapterServiceEventType.AuthorizeDapp;
    identityName: string | null;
    identityUri: string | null;
    iconRelativeUri: string | null;
    cluster: string;

    constructor(cluster: string, identityName: string | null, identityUri: string | null, iconRelativeUri: string | null,) {
        super();
        this.cluster = cluster;
        this.identityName = identityName;
        this.identityUri = identityUri;
        this.iconRelativeUri = iconRelativeUri;
    }

    completeWithAuthorize(publicKey: Uint8Array, accountLabel: string | null, walletUriBase: string | null, authorizationScope: Uint8Array | null): void {
        console.log(this.type + ': completeWithAuthorize: authorized public key =', publicKey);
        const bridgedTypePublicKey: number[] = Array.from(publicKey);
        SolanaMobileWalletAdapterWalletLib.completeWithAuthorize(bridgedTypePublicKey, accountLabel, walletUriBase, authorizationScope)
      }
    
    completeWithDecline(): void {
        console.log(this.type + ': completeAuthorizeWithDecline');
        SolanaMobileWalletAdapterWalletLib.completeAuthorizeWithDecline();
    }
}

export class ReauthorizeDappRequest extends MobileWalletAdapterServiceRemoteRequest {
    type: MobileWalletAdapterServiceEventType = MobileWalletAdapterServiceEventType.ReauthorizeDapp;
    
    // TODO: implement ReauthorizeDappRequest
    completeWithDecline(): void {
        console.log(this.type + ': completeAuthorizeWithDecline');
    }
}

export abstract class SignPayloadsRequest extends MobileWalletAdapterServiceRemoteRequest 
        implements MobileWalletAdapterServiceRemoteRequest {
    abstract type: MobileWalletAdapterServiceEventType;
    payloads: Uint8Array[];

    constructor(payloads: Uint8Array[]) {
        super();
        this.payloads = payloads;
    }
    
    completeWithSignedPayloads(signedPayloads: Uint8Array[]): void {
        console.log(this.type + ': completeSignPayloadsRequest: signedPayloads =', signedPayloads);
        const bridgeTypedPayloads: number[][] = signedPayloads.map((byteArray) => {
            return Array.from(byteArray);
        })
        SolanaMobileWalletAdapterWalletLib.completeWithSignedPayloads(bridgeTypedPayloads)
    }

    completeWithInvalidPayloads(validArray: boolean[]): void {
        console.log(this.type + ': completeWithInvalidPayloads: validArray =', validArray);
        SolanaMobileWalletAdapterWalletLib.completeWithInvalidPayloads(validArray)
    }

    completeWithDecline(): void {
        console.log(this.type + ': completeSignPayloadsWithDecline');
        SolanaMobileWalletAdapterWalletLib.completeSignPayloadsWithDecline();
    }

    completeSignPayloadsWithTooManyPayloads(): void {
        console.log(this.type + ': completeWithTooManyPayloads');
        SolanaMobileWalletAdapterWalletLib.completeSignPayloadsWithTooManyPayloads();
    }

    completeSignPayloadsWithAuthorizationNotValid(): void {
        console.log(this.type + ': completeSignPayloadsWithAuthorizationNotValid');
        SolanaMobileWalletAdapterWalletLib.completeSignPayloadsWithAuthorizationNotValid();
    }
}

export class SignMessagesRequest extends SignPayloadsRequest {
    type: MobileWalletAdapterServiceEventType = MobileWalletAdapterServiceEventType.SignMessages;
  
    constructor(payloads: Uint8Array[]) {
      super(payloads)
    }
  }
  
  export class SignTransactionsRequest extends SignPayloadsRequest {
    type: MobileWalletAdapterServiceEventType = MobileWalletAdapterServiceEventType.SignTransactions;

    constructor(payloads: Uint8Array[]) {
        super(payloads)
    }
  }
  
  export class SignAndSendTransactionsRequest extends MobileWalletAdapterServiceRemoteRequest
          implements MobileWalletAdapterServiceRemoteRequest {

    type: MobileWalletAdapterServiceEventType = MobileWalletAdapterServiceEventType.SignAndSendTransactions;
    payloads: Uint8Array[];
    minContextSlot?: string;
  
    constructor(payloads: Uint8Array[], minContextSlot?: string) {
      super();
      this.payloads = payloads;
      this.minContextSlot = minContextSlot;
    }
  
    completeWithSignatures(signatures: Uint8Array[]): void {
      console.log(this.type + ': completeWithSignatures: signatures =', signatures);
      const bridgeTypedsignatures: number[][] = signatures.map((byteArray) => {
        return Array.from(byteArray);
    })
      SolanaMobileWalletAdapterWalletLib.completeWithSignatures(bridgeTypedsignatures)
    }
  
    completeWithInvalidSignatures(validArray: boolean[]): void {
      console.log(this.type + ': completeWithInvalidSignatures: validArray =', validArray);
      SolanaMobileWalletAdapterWalletLib.completeWithInvalidSignatures(validArray)
    }
  
    completeWithDecline(): void {
      console.log(this.type + ': completeSignAndSendWithDecline');
      SolanaMobileWalletAdapterWalletLib.completeSignAndSendWithDecline();
    }
  
    completeSignAndSendWithTooManyPayloads(): void {
      console.log(this.type + ': completeSignAndSendWithTooManyPayloads');
      SolanaMobileWalletAdapterWalletLib.completeSignAndSendWithTooManyPayloads();
    }
  
    completeSignAndSendWithAuthorizationNotValid(): void {
      console.log(this.type + ': completeSignAndSendWithAuthorizationNotValid');
      SolanaMobileWalletAdapterWalletLib.completeSignAndSendWithAuthorizationNotValid();
    }
}
  