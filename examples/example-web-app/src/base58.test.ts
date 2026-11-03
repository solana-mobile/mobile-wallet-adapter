import { describe, expect, it } from 'vitest';

import { base58Decode, base58Encode } from './base58.js';

describe('base58', () => {
    it('round-trips bytes, keeping leading zeros', () => {
        const bytes = Uint8Array.of(0, 0, 1, 2, 255, 128);
        expect(base58Decode(base58Encode(bytes))).toEqual(bytes);
        expect(base58Encode(Uint8Array.of(0, 0, 1))).toBe('112');
    });

    it('decodes a known program id to 32 bytes', () => {
        const decoded = base58Decode('MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr');
        expect(decoded).toHaveLength(32);
        expect(base58Encode(decoded)).toBe('MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr');
    });

    it('rejects characters outside the alphabet', () => {
        expect(() => base58Decode('0OIl')).toThrow('Invalid base58 character: 0');
    });
});
