export enum MobileWalletAdapterSessionEventType {
    None = 'NONE',
    SessionTerminated = 'SESSION_TERMINATED',
    SessionReady = 'SESSION_READY',
    SessionServingClients = 'SESSION_SERVING_CLIENTS',
    SessionServingComplete = 'SESSION_SERVING_COMPLETE',
    SessionComplete = 'SESSION_COMPLETE',
    SessionError = 'SESSION_ERROR',
    SessionTeardownComplete = 'SESSION_TEARDOWN_COMPLETE',
    LowPowerNoConnection = 'LOW_POWER_NO_CONNECTION',
}

export abstract class MobileWalletAdapterSessionEvent {
    type: MobileWalletAdapterSessionEventType = MobileWalletAdapterSessionEventType.None;
}

export class NoneEvent extends MobileWalletAdapterSessionEvent {
    type: MobileWalletAdapterSessionEventType = MobileWalletAdapterSessionEventType.None;
}

export class SessionTerminatedEvent extends MobileWalletAdapterSessionEvent {
    type: MobileWalletAdapterSessionEventType = MobileWalletAdapterSessionEventType.SessionTerminated;
}

export class SessionReadyEvent extends MobileWalletAdapterSessionEvent {
    type: MobileWalletAdapterSessionEventType = MobileWalletAdapterSessionEventType.SessionReady;
}

export class SessionServingClientsEvent extends MobileWalletAdapterSessionEvent {
    type: MobileWalletAdapterSessionEventType = MobileWalletAdapterSessionEventType.SessionServingClients;
}

export class SessionServingCompleteEvent extends MobileWalletAdapterSessionEvent {
    type: MobileWalletAdapterSessionEventType = MobileWalletAdapterSessionEventType.SessionServingComplete;
}

export class SessionCompleteEvent extends MobileWalletAdapterSessionEvent {
    type: MobileWalletAdapterSessionEventType = MobileWalletAdapterSessionEventType.SessionComplete;
}

export class SessionErrorEvent extends MobileWalletAdapterSessionEvent {
    type: MobileWalletAdapterSessionEventType = MobileWalletAdapterSessionEventType.SessionError;
}

export class SessionTeardownCompleteEvent extends MobileWalletAdapterSessionEvent {
    type: MobileWalletAdapterSessionEventType = MobileWalletAdapterSessionEventType.SessionTeardownComplete;
}

export class LowPowerNoConnectionEvent extends MobileWalletAdapterSessionEvent {
    type: MobileWalletAdapterSessionEventType = MobileWalletAdapterSessionEventType.LowPowerNoConnection;
}
