import getAttestationEngine from './getAttestationEngine';
import { OriginAttestationMessageType } from './messageTypes';
import { OriginAttestationToken } from './types';

export default async function attestOrigin(
    attestationEngineEndpoint: URL,
    { abortSignal }: { abortSignal: AbortSignal },
): Promise<OriginAttestationToken> {
    const { destroy, getPromiseForMessage, sendMessage } = await getAttestationEngine(
        attestationEngineEndpoint,
        abortSignal,
    );
    function cleanup() {
        abortSignal.removeEventListener('abort', handleAbort);
        destroy();
    }
    function handleAbort() {
        cleanup();
    }
    abortSignal.addEventListener('abort', handleAbort);
    try {
        sendMessage({
            __type: OriginAttestationMessageType.AttestOrigin,
            hi: 'there',
        });
        const attestationGrantMessage = await getPromiseForMessage(
            OriginAttestationMessageType.GrantOriginAttestationToken,
            abortSignal,
        );
        return attestationGrantMessage.originAttestationToken;
    } finally {
        cleanup();
    }
}
