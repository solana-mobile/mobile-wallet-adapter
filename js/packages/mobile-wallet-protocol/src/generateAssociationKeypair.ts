export default async function generateAssociationKeypair(): Promise<CryptoKeyPair> {
    return await crypto.subtle.generateKey(
        {
            name: 'ECDSA',
            namedCurve: 'P-256',
        },
        false /* extractable */,
        ['sign'] /* keyUsages */,
    );
}
