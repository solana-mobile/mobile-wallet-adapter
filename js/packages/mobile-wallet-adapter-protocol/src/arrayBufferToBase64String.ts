import { fromUint8Array } from './base64Utils.js';

export function arrayBufferToBase64String(buffer: ArrayBuffer): string {
    return fromUint8Array(new Uint8Array(buffer));
}

/**
 * @deprecated Use {@link arrayBufferToBase64String} instead.
 */
export default arrayBufferToBase64String;
