export default async function generateECDHKeypair(): Promise<CryptoKeyPair> {
    return await crypto.subtle.generateKey(
        {
            name: 'ECDH',
            namedCurve: 'P-256',
        },
        false /* extractable */,
        ['deriveKey', 'deriveBits'] /* keyUsages */,
    );
}
