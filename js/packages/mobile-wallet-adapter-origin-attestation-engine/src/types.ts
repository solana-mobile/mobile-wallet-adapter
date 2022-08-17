import { OriginAttestationMessageType } from './messageTypes';

export interface AttestationEngine {
    attest(): OriginAttestationToken;
    destroy(): void;
}

export type AttestOriginOptions = Readonly<{
    signal: AbortSignal;
}>;

export type OriginAttestationToken = string;

export type Message = Readonly<
    | { __type: OriginAttestationMessageType.AttestOrigin; hi: 'there' }
    | { __type: OriginAttestationMessageType.EngineReady }
    | { __type: OriginAttestationMessageType.GenerateOriginAttestationKeypair }
    | { __type: OriginAttestationMessageType.GrantOriginAttestationToken; originAttestationToken: string }
    | { __type: OriginAttestationMessageType.IssueOriginAttestationPublicKey; publicKey: Uint8Array }
>;
