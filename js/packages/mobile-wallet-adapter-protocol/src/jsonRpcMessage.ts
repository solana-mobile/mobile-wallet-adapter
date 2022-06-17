import { SolanaMobileWalletAdapterProtocolJsonRpcError } from './errors';
import { SharedSecret } from './parseHelloRsp';

const INITIALIZATION_VECTOR_BYTES = 12;

type JSONRPCResponse<TMessage> = {
    id: number;
    jsonrpc: '2.0';
    result: TMessage;
};

export async function encryptJsonRpcMessage(jsonRpcMessage: unknown, sharedSecret: SharedSecret) {
    const plaintext = JSON.stringify(jsonRpcMessage);
    const initializationVector = new Uint8Array(INITIALIZATION_VECTOR_BYTES);
    crypto.getRandomValues(initializationVector);
    const ciphertext = await crypto.subtle.encrypt(
        getAlgorithmParams(initializationVector),
        sharedSecret,
        Buffer.from(plaintext),
    );
    const response = new Uint8Array(initializationVector.byteLength + ciphertext.byteLength);
    response.set(new Uint8Array(initializationVector), 0);
    response.set(new Uint8Array(ciphertext), initializationVector.byteLength);
    return response;
}

export async function decryptJsonRpcMessage<TMessage>(message: ArrayBuffer, sharedSecret: SharedSecret) {
    const initializationVector = message.slice(0, INITIALIZATION_VECTOR_BYTES);
    const ciphertext = message.slice(INITIALIZATION_VECTOR_BYTES);
    const plaintextBuffer = await crypto.subtle.decrypt(
        getAlgorithmParams(initializationVector),
        sharedSecret,
        ciphertext,
    );
    const plaintext = getUtf8Decoder().decode(plaintextBuffer);
    const jsonRpcMessage = JSON.parse(plaintext);
    if (Object.hasOwnProperty.call(jsonRpcMessage, 'error')) {
        throw new SolanaMobileWalletAdapterProtocolJsonRpcError(
            jsonRpcMessage.id,
            jsonRpcMessage.error.code,
            jsonRpcMessage.error.message,
        );
    }
    return jsonRpcMessage as JSONRPCResponse<TMessage>;
}

function getAlgorithmParams(initializationVector: ArrayBuffer) {
    return {
        iv: initializationVector,
        name: 'AES-GCM',
        tagLength: 128, // 16 byte tag => 128 bits
    };
}

let _utf8Decoder: TextDecoder | undefined;
function getUtf8Decoder() {
    if (_utf8Decoder === undefined) {
        _utf8Decoder = new TextDecoder('utf-8');
    }
    return _utf8Decoder;
}
