// @vitest-environment jsdom
import { describe, expect, it } from 'vitest';

import arrayBufferToBase64String from '../src/arrayBufferToBase64String.js';

describe('arrayBufferToBase64String', () => {
    it('encodes an array buffer to base64', () => {
        const bytes = Uint8Array.of(0, 1, 2, 253, 254, 255);

        expect(arrayBufferToBase64String(bytes.buffer)).toBe('AAEC/f7/');
    });
});
