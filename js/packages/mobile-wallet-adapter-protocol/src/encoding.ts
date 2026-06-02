import { getUtf8Decoder, getUtf8Encoder } from '@solana/kit';

import {
    base64ToBase58 as internalBase64ToBase58,
    fromUint8Array as internalBase58FromUint8Array,
    toUint8Array as internalBase58ToUint8Array,
} from './base58Utils.js';
import {
    encode as internalBase64EncodeString,
    fromUint8Array as internalBase64FromUint8Array,
    toUint8Array as internalBase64ToUint8Array,
} from './base64Utils.js';

export { arrayBufferToBase64String } from './arrayBufferToBase64String.js';

export function base58FromUint8Array(byteArray: Uint8Array): string {
    return internalBase58FromUint8Array(byteArray);
}

export function base58ToUint8Array(base58EncodedByteArray: string): Uint8Array {
    return internalBase58ToUint8Array(base58EncodedByteArray);
}

export function base64EncodeString(input: string): string {
    return internalBase64EncodeString(input);
}

export function base64FromUint8Array(byteArray: Uint8Array): string {
    return internalBase64FromUint8Array(byteArray);
}

export function base64ToBase58(base64EncodedString: string): string {
    return internalBase64ToBase58(base64EncodedString);
}

export function base64ToUint8Array(base64EncodedByteArray: string): Uint8Array {
    return internalBase64ToUint8Array(base64EncodedByteArray);
}

export function base64UrlFromUint8Array(byteArray: Uint8Array): string {
    return internalBase64FromUint8Array(byteArray, true);
}

export function utf8FromUint8Array(byteArray: Uint8Array): string {
    return getUtf8Decoder().decode(byteArray);
}

export function utf8ToUint8Array(input: string): Uint8Array {
    return getUtf8Encoder().encode(input) as Uint8Array;
}
