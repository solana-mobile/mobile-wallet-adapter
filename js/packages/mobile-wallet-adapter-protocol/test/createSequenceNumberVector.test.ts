import { describe, expect, it } from 'vitest';

import createSequenceNumberVector from '../src/createSequenceNumberVector.js';

describe('createSequenceNumberVector', () => {
    it('encodes a sequence number in big-endian byte order', () => {
        expect(createSequenceNumberVector(0x01020304)).toEqual(Uint8Array.of(1, 2, 3, 4));
    });

    it('rejects sequence numbers that overflow the 32-bit range', () => {
        expect(() => createSequenceNumberVector(4294967296)).toThrow(/Outbound sequence number overflow/);
    });
});
