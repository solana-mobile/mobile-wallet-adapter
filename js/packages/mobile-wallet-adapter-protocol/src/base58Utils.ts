import { getBase58Decoder, getBase58Encoder } from '@solana/kit';

import { toUint8Array as base64ToUint8Array } from './base64Utils.js';

export function fromUint8Array(byteArray: Uint8Array): string {
    return getBase58Decoder().decode(byteArray);
}

export function toUint8Array(base58EncodedByteArray: string): Uint8Array {
    return getBase58Encoder().encode(base58EncodedByteArray) as Uint8Array;
}

export function base64ToBase58(base64EncodedString: string): string {
    return fromUint8Array(base64ToUint8Array(base64EncodedString));
}
