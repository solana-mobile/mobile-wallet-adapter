// @vitest-environment jsdom
import { describe, expect, it } from 'vitest';

import { fromUint8Array, toUint8Array } from '../src/base64Utils';

describe('base64Utils', () => {
    it('encodes bytes to a base64 string', () => {
        const bytes = Uint8Array.of(0, 1, 2, 253, 254, 255);

        expect(fromUint8Array(bytes)).toBe('AAEC/f7/');
    });

    it('decodes a base64 string to bytes', () => {
        expect(toUint8Array('AAEC/f7/')).toEqual(Uint8Array.of(0, 1, 2, 253, 254, 255));
    });

    it('round-trips bytes through base64 helpers', () => {
        const bytes = Uint8Array.of(0, 1, 2, 253, 254, 255);

        expect(toUint8Array(fromUint8Array(bytes))).toEqual(bytes);
    });
});
