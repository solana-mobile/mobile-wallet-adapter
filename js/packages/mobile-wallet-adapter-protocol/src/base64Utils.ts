import { getBase64Decoder, getBase64Encoder, getUtf8Encoder } from '@solana/kit';

export function encode(input: string): string {
    return getBase64Decoder().decode(getUtf8Encoder().encode(input));
}

export function fromUint8Array(byteArray: Uint8Array, urlsafe?: boolean): string {
    const base64 = getBase64Decoder().decode(byteArray);
    if (urlsafe) {
        return base64.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
    } else return base64;
}

export function toUint8Array(base64EncodedByteArray: string): Uint8Array {
    return getBase64Encoder().encode(base64EncodedByteArray) as Uint8Array;
}
