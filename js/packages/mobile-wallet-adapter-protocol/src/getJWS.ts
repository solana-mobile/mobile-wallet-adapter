import { arrayBufferToBase64String } from './arrayBufferToBase64String.js';
import { encode } from './base64Utils.js';
import { utf8ToUint8Array } from './encoding.js';

export default async function getJWS(payload: string, privateKey: CryptoKey) {
    const header = { alg: 'ES256' };
    const headerEncoded = encode(JSON.stringify(header));
    const payloadEncoded = encode(payload);
    const message = `${headerEncoded}.${payloadEncoded}`;
    const signatureBuffer = await crypto.subtle.sign(
        {
            name: 'ECDSA',
            hash: 'SHA-256',
        },
        privateKey,
        utf8ToUint8Array(message),
    );
    const signature = arrayBufferToBase64String(signatureBuffer);
    const jws = `${message}.${signature}`;
    return jws;
}
