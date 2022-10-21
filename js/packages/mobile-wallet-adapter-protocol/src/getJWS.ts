import arrayBufferToBase64String from './arrayBufferToBase64String.js';

export default async function getJWS(payload: string, privateKey: CryptoKey) {
    const header = { alg: 'ES256' };
    const headerEncoded = window.btoa(JSON.stringify(header));
    const payloadEncoded = window.btoa(payload);
    const message = `${headerEncoded}.${payloadEncoded}`;
    const signatureBuffer = await crypto.subtle.sign(
        {
            name: 'ECDSA',
            hash: 'SHA-256',
        },
        privateKey,
        new TextEncoder().encode(message),
    );
    const signature = arrayBufferToBase64String(signatureBuffer);
    const jws = `${message}.${signature}`;
    return jws;
}
