import { SolanaMobileWalletAdapterProtocolError } from './errors';
import { SharedSecret } from './parseHelloRsp';

const SEQUENCE_NUMBER_BYTES = 4;
const INITIALIZATION_VECTOR_BYTES = 12;

type JSONRPCResponse<TMessage> = {
    id: number;
    jsonrpc: '2.0';
    result: TMessage;
};

export async function encryptJsonRpcMessage(jsonRpcMessage: unknown, sharedSecret: SharedSecret) {
    const plaintext = JSON.stringify(jsonRpcMessage);
    const sequenceNumber = new Uint8Array(SEQUENCE_NUMBER_BYTES);
    const initializationVector = new Uint8Array(INITIALIZATION_VECTOR_BYTES);
    // TODO: populate sequence number (big-endian)
    crypto.getRandomValues(initializationVector);
    const ciphertext = await crypto.subtle.encrypt(
        getAlgorithmParams(sequenceNumber, initializationVector),
        sharedSecret,
        Buffer.from(plaintext),
    );
    const response = new Uint8Array(initializationVector.byteLength + ciphertext.byteLength);
    response.set(new Uint8Array(sequenceNumber), 0);
    response.set(new Uint8Array(initializationVector), sequenceNumber.byteLength);
    response.set(new Uint8Array(ciphertext), sequenceNumber.byteLength + initializationVector.byteLength);
    return response;
}

export async function decryptJsonRpcMessage<TMessage>(message: ArrayBuffer, sharedSecret: SharedSecret) {
    const sequenceNumber = message.slice(0, SEQUENCE_NUMBER_BYTES);
    // TODO: verify sequence number (big-endian)
    const initializationVector = message.slice(SEQUENCE_NUMBER_BYTES, SEQUENCE_NUMBER_BYTES + INITIALIZATION_VECTOR_BYTES);
    const ciphertext = message.slice(SEQUENCE_NUMBER_BYTES + INITIALIZATION_VECTOR_BYTES);
    const plaintextBuffer = await crypto.subtle.decrypt(
        getAlgorithmParams(sequenceNumber, initializationVector),
        sharedSecret,
        ciphertext,
    );
    const plaintext = getUtf8Decoder().decode(plaintextBuffer);
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

function getAlgorithmParams(sequenceNumber: ArrayBuffer, initializationVector: ArrayBuffer) {
    return {
        additionalData: sequenceNumber,
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
