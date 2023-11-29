import createSequenceNumberVector, { SEQUENCE_NUMBER_BYTES } from './createSequenceNumberVector.js';
import { SharedSecret } from './parseHelloRsp.js';

const INITIALIZATION_VECTOR_BYTES = 12;
export const ENCODED_PUBLIC_KEY_LENGTH_BYTES = 65;

export async function encryptMessage(
    plaintext: string,
    sequenceNumber: number,
    sharedSecret: SharedSecret,
) {
    const sequenceNumberVector = createSequenceNumberVector(sequenceNumber);
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

export async function decryptMessage(message: ArrayBuffer, sharedSecret: SharedSecret) {
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
    return plaintext
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