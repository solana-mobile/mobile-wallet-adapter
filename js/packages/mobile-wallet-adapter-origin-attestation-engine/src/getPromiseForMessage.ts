import { OriginAttestationMessageType } from './messageTypes';
import { Message } from './types';

export default async function getPromiseForMessage<TMessage extends OriginAttestationMessageType>(
    contentWindow: Window,
    messageType: TMessage,
    abortSignal: AbortSignal,
): Promise<Extract<Message, { __type: TMessage }>> {
    return new Promise((resolve, reject) => {
        function cleanup() {
            abortSignal.removeEventListener('abort', handleAbort);
            globalThis.window.removeEventListener('message', handleMessage);
        }
        function handleAbort() {
            cleanup();
            reject(abortSignal.reason);
        }
        abortSignal.addEventListener('abort', handleAbort);
        function handleMessage(evt: MessageEvent) {
            if (evt.source == null || evt.source !== contentWindow) {
                return;
            }
            if (evt.data.__type === messageType) {
                cleanup();
                resolve(evt.data);
            }
        }
        globalThis.window.addEventListener('message', handleMessage);
    });
}
