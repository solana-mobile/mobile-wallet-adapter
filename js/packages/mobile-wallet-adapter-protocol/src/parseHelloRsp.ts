import { ENCODED_PUBLIC_KEY_LENGTH_BYTES } from "./encryptedMessage";

/**
 * A secret agreed upon by the app and the wallet. Used as
 * a symmetric key to encrypt and decrypt messages over an
 * unsecured channel.
 */
export type SharedSecret = CryptoKey;

export default async function parseHelloRsp(
    payloadBuffer: ArrayBuffer, // The X9.62-encoded wallet endpoint ephemeral ECDH public keypoint.
    associationPublicKey: CryptoKey,
    ecdhPrivateKey: CryptoKey,
): Promise<SharedSecret> {
    const [associationPublicKeyBuffer, walletPublicKey] = await Promise.all([
        crypto.subtle.exportKey('raw', associationPublicKey),
        crypto.subtle.importKey(
            'raw',
            payloadBuffer.slice(0, ENCODED_PUBLIC_KEY_LENGTH_BYTES),
            { name: 'ECDH', namedCurve: 'P-256' },
            false /* extractable */,
            [] /* keyUsages */,
        ),
    ]);
    const sharedSecret = await crypto.subtle.deriveBits({ name: 'ECDH', public: walletPublicKey }, ecdhPrivateKey, 256);
    const ecdhSecretKey = await crypto.subtle.importKey(
        'raw',
        sharedSecret,
        'HKDF',
        false /* extractable */,
        ['deriveKey'] /* keyUsages */,
    );
    const aesKeyMaterialVal = await crypto.subtle.deriveKey(
        {
            name: 'HKDF',
            hash: 'SHA-256',
            salt: new Uint8Array(associationPublicKeyBuffer),
            info: new Uint8Array(),
        },
        ecdhSecretKey,
        { name: 'AES-GCM', length: 128 },
        false /* extractable */,
        ['encrypt', 'decrypt'],
    );
    return aesKeyMaterialVal as SharedSecret;
}
