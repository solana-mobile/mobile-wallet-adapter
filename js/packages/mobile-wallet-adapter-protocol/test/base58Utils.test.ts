// @vitest-environment jsdom
import { describe, expect, it } from 'vitest';

import { base64ToBase58, fromUint8Array } from '../src/base58Utils.js';

const BASE58_ENCODED_BYTES = '17cy7x2';
const BASE64_ENCODED_BYTES = 'AAEC/f7/';
const BYTES = Uint8Array.of(0, 1, 2, 253, 254, 255);

describe('base58Utils', () => {
    it('converts a base64 string to a base58 string', () => {
        expect(base64ToBase58(BASE64_ENCODED_BYTES)).toBe(BASE58_ENCODED_BYTES);
    });

    it('encodes bytes to a base58 string', () => {
        expect(fromUint8Array(BYTES)).toBe(BASE58_ENCODED_BYTES);
    });
});
