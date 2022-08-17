import getPromiseForMessage from './getPromiseForMessage';
import { OriginAttestationMessageType } from './messageTypes';
import sendMessage from './sendMessage';
import { Message } from './types';

export default function getAttestee() {
    const targetWindow = globalThis.parent;
    return {
        getPromiseForMessage<TMessage extends OriginAttestationMessageType>(
            messageType: TMessage,
            abortSignal: AbortSignal,
        ): Promise<Extract<Message, { __type: TMessage }>> {
            return getPromiseForMessage(targetWindow, messageType, abortSignal);
        },
        sendMessage(message: Message) {
            sendMessage(targetWindow, targetWindow.origin, message);
        },
    };
}
