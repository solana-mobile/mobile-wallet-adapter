export default async function createHelloReq(ecdhPublicKey: CryptoKey, associationKeypairPrivateKey: CryptoKey) {
    const publicKeyBuffer = await crypto.subtle.exportKey('raw', ecdhPublicKey);
    const signatureBuffer = await crypto.subtle.sign(
        { hash: 'SHA-256', name: 'ECDSA' },
        associationKeypairPrivateKey,
        publicKeyBuffer,
    );
    const response = new Uint8Array(publicKeyBuffer.byteLength + signatureBuffer.byteLength);
    response.set(new Uint8Array(publicKeyBuffer), 0);
    response.set(new Uint8Array(signatureBuffer), publicKeyBuffer.byteLength);
    return response;
}
