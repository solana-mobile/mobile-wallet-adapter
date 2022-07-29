import getDidTimeoutPromise from './getDidTimeoutPromise';
import getPromiseForMessage from './getPromiseForMessage';
import { OriginAttestationMessageType } from './messageTypes';
import sendMessage from './sendMessage';
import { Message } from './types';

export default async function getAttestationEngine(attestationEngineEndpoint: URL, abortSignal: AbortSignal) {
    let contentWindow: Window;
    function cleanup() {
        abortSignal.removeEventListener('abort', handleAbort);
        frame.parentNode?.removeChild(frame);
    }
    function handleAbort() {
        cleanup();
    }
    abortSignal.addEventListener('abort', handleAbort);
    const frame = document.createElement('iframe');
    frame.style.display = 'none';
    frame.setAttribute('height', '0');
    frame.setAttribute('tabindex', '-1');
    frame.setAttribute('width', '0');
    globalThis.document.body.appendChild(frame);
    try {
        if (frame.contentWindow == null) {
            // Beware; this only materializes *after* the frame is appended to the body element.
            throw new Error('TODO: no contentWindow');
        }
        contentWindow = frame.contentWindow;
        const engineReadyPromise = Promise.race([
            getPromiseForMessage(contentWindow, OriginAttestationMessageType.EngineReady, abortSignal),
            getDidTimeoutPromise(contentWindow, abortSignal),
        ]);
        frame.src = attestationEngineEndpoint.toString();
        await engineReadyPromise;
        const targetOrigin = `${attestationEngineEndpoint.protocol}//${attestationEngineEndpoint.hostname}:${attestationEngineEndpoint.port}`;
        return {
            destroy: cleanup,
            getPromiseForMessage<TMessage extends OriginAttestationMessageType>(
                messageType: TMessage,
                abortSignal: AbortSignal,
            ): Promise<Extract<Message, { __type: TMessage }>> {
                return getPromiseForMessage(contentWindow, messageType, abortSignal);
            },
            sendMessage(message: Message) {
                sendMessage(contentWindow, targetOrigin, message);
            },
        };
    } catch (e) {
        cleanup();
        throw e;
    }
}
