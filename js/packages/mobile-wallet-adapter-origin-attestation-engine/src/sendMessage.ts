import { Message } from './types';

export default function sendMessage(contentWindow: Window, targetOrigin: string, message: Message) {
    contentWindow.postMessage(message, targetOrigin);
}
