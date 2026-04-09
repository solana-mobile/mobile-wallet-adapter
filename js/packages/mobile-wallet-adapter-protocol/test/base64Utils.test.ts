// @vitest-environment jsdom
import { describe, expect, it } from 'vitest';

import { encode, fromUint8Array, toUint8Array } from '../src/base64Utils.js';

const BASE64_ENCODED_BYTES = 'AAEC/f7/';
const BASE64_ENCODED_STRING = 'aGVsbG8=';
const BYTES = Uint8Array.of(0, 1, 2, 253, 254, 255);
const URL_SAFE_BASE64_ENCODED_BYTES = '-_8';
const URL_SAFE_BYTES = Uint8Array.of(251, 255);

describe('base64Utils', () => {
    it('encodes a string to base64', () => {
        expect(encode('hello')).toBe(BASE64_ENCODED_STRING);
    });

    it('encodes bytes to a base64 string', () => {
        expect(fromUint8Array(BYTES)).toBe(BASE64_ENCODED_BYTES);
    });

    it('encodes bytes to a URL-safe base64 string', () => {
        expect(fromUint8Array(URL_SAFE_BYTES, true)).toBe(URL_SAFE_BASE64_ENCODED_BYTES);
    });

    it('decodes a base64 string to bytes', () => {
        expect(toUint8Array(BASE64_ENCODED_BYTES)).toEqual(BYTES);
    });

    it('round-trips bytes through the base64 helpers', () => {
        expect(toUint8Array(fromUint8Array(BYTES))).toEqual(BYTES);
    });
});
