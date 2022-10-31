import createSequenceNumberVector, { SEQUENCE_NUMBER_BYTES } from './createSequenceNumberVector.js';
import { SolanaMobileWalletAdapterProtocolError } from './errors.js';
import { SharedSecret } from './parseHelloRsp.js';

const INITIALIZATION_VECTOR_BYTES = 12;

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
    const sequenceNumberVector = createSequenceNumberVector(jsonRpcMessage.id);
    const initializationVector = new Uint8Array(INITIALIZATION_VECTOR_BYTES);
    crypto.getRandomValues(initializationVector);
    const ciphertext = await crypto.subtle.encrypt(
        getAlgorithmParams(sequenceNumberVector, initializationVector),
        sharedSecret,
        new TextEncoder().encode(plaintext),
    );
    const response = new Uint8Array(
        sequenceNumberVector.byteLength + initializationVector.byteLength + ciphertext.byteLength,
    );
    response.set(new Uint8Array(sequenceNumberVector), 0);
    response.set(new Uint8Array(initializationVector), sequenceNumberVector.byteLength);
    response.set(new Uint8Array(ciphertext), sequenceNumberVector.byteLength + initializationVector.byteLength);
    return response;
}

export async function decryptJsonRpcMessage<TMessage>(message: ArrayBuffer, sharedSecret: SharedSecret) {
    const sequenceNumberVector = message.slice(0, SEQUENCE_NUMBER_BYTES);
    const initializationVector = message.slice(
        SEQUENCE_NUMBER_BYTES,
        SEQUENCE_NUMBER_BYTES + INITIALIZATION_VECTOR_BYTES,
    );
    const ciphertext = message.slice(SEQUENCE_NUMBER_BYTES + INITIALIZATION_VECTOR_BYTES);
    const plaintextBuffer = await crypto.subtle.decrypt(
        getAlgorithmParams(sequenceNumberVector, initializationVector),
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
