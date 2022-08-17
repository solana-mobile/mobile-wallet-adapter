import getAttestationEngine from './getAttestationEngine';
import { OriginAttestationMessageType } from './messageTypes';

type PublicKey = Uint8Array;

export default async function generateKeypair(
    attestationEngineEndpoint: URL,
    { abortSignal }: { abortSignal: AbortSignal },
): Promise<PublicKey> {
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
            __type: OriginAttestationMessageType.GenerateOriginAttestationKeypair,
        });
        const issuedPublicKeyMessage = await getPromiseForMessage(
            OriginAttestationMessageType.IssueOriginAttestationPublicKey,
            abortSignal,
        );
        return issuedPublicKeyMessage.publicKey;
    } finally {
        cleanup();
    }
}
