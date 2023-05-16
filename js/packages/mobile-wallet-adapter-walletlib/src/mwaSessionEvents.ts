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

export type SessionStartEvent = Readonly<{
    __type: MWASessionEventType.SessionStartEvent;
}> &
    IMWASessionEvent;

export type SessionReadyEvent = Readonly<{
    __type: MWASessionEventType.SessionReadyEvent;
}> &
    IMWASessionEvent;

export type SessionTerminatedEvent = Readonly<{
    __type: MWASessionEventType.SessionTerminatedEvent;
}> &
    IMWASessionEvent;

export type SessionServingClientsEvent = Readonly<{
    __type: MWASessionEventType.SessionServingClientsEvent;
}> &
    IMWASessionEvent;

export type SessionServingCompleteEvent = Readonly<{
    __type: MWASessionEventType.SessionServingCompleteEvent;
}> &
    IMWASessionEvent;

export type SessionCompleteEvent = Readonly<{
    __type: MWASessionEventType.SessionCompleteEvent;
}> &
    IMWASessionEvent;

export type SessionErrorEvent = Readonly<{
    __type: MWASessionEventType.SessionErrorEvent;
}> &
    IMWASessionEvent;

export type SessionTeardownCompleteEvent = Readonly<{
    __type: MWASessionEventType.SessionTeardownCompleteEvent;
}> &
    IMWASessionEvent;

export type LowPowerNoConnectionEvent = Readonly<{
    __type: MWASessionEventType.LowPowerNoConnectionEvent;
}> &
    IMWASessionEvent;

export type MWASessionEvent =
    | SessionStartEvent
    | SessionReadyEvent
    | SessionTerminatedEvent
    | SessionServingClientsEvent
    | SessionServingCompleteEvent
    | SessionCompleteEvent
    | SessionErrorEvent
    | SessionTeardownCompleteEvent
    | LowPowerNoConnectionEvent;
