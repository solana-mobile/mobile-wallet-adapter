// @vitest-environment jsdom
import { describe, expect, it } from 'vitest';

import { fromUint8Array, toUint8Array } from '../src/base64Utils.js';

const BYTES = Uint8Array.of(0, 1, 2, 253, 254, 255);

describe('base64Utils', () => {
    it('encodes bytes to a base64 string', () => {
        expect(fromUint8Array(BYTES)).toBe('AAEC/f7/');
    });

    it('decodes a base64 string to bytes', () => {
        expect(toUint8Array('AAEC/f7/')).toEqual(BYTES);
    });

    it('round-trips bytes through the browser helpers', () => {
        expect(toUint8Array(fromUint8Array(BYTES))).toEqual(BYTES);
    });
});
