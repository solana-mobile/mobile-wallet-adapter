import { decryptMessage, encryptMessage } from './encryptedMessage.js';
import { SolanaMobileWalletAdapterProtocolError } from './errors.js';
import { SharedSecret } from './parseHelloRsp.js';

interface JSONRPCRequest<TParams> {
    id: number;
    jsonrpc: '2.0';
    method: string;
    params: TParams;
}

type JSONRPCResponse<TMessage> = {
    id: number;
    jsonrpc: '2.0';
    result: TMessage;
};

export async function encryptJsonRpcMessage<TParams>(
    jsonRpcMessage: JSONRPCRequest<TParams>,
    sharedSecret: SharedSecret,
) {
    const plaintext = JSON.stringify(jsonRpcMessage);
    const sequenceNumber = jsonRpcMessage.id;
    return encryptMessage(plaintext, sequenceNumber, sharedSecret);
}

export async function decryptJsonRpcMessage<TMessage>(message: ArrayBuffer, sharedSecret: SharedSecret) {
    const plaintext = await decryptMessage(message, sharedSecret);
    const jsonRpcMessage = JSON.parse(plaintext);
    if (Object.hasOwnProperty.call(jsonRpcMessage, 'error')) {
        throw new SolanaMobileWalletAdapterProtocolError<typeof jsonRpcMessage.error.code>(
            jsonRpcMessage.id,
            jsonRpcMessage.error.code,
            jsonRpcMessage.error.message,
        );
    }
    return jsonRpcMessage as JSONRPCResponse<TMessage>;
}
