// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import getJWS from '../src/getJWS.js';

const PAYLOAD = 'payload';
const PRIVATE_KEY = {} as CryptoKey;
const SIGNATURE_BYTES = Uint8Array.of(1, 2, 3);

const { mockSign } = vi.hoisted(() => ({
    mockSign: vi.fn(),
}));

beforeEach(() => {
    mockSign.mockResolvedValue(SIGNATURE_BYTES.buffer);
    vi.stubGlobal('crypto', {
        subtle: {
            sign: mockSign,
        },
    });
});

afterEach(() => {
    mockSign.mockReset();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
});

describe('getJWS', () => {
    it('creates an ES256 JWS from the encoded header, payload, and signature', async () => {
        const headerEncoded = window.btoa(JSON.stringify({ alg: 'ES256' }));
        const payloadEncoded = window.btoa(PAYLOAD);
        const message = `${headerEncoded}.${payloadEncoded}`;

        await expect(getJWS(PAYLOAD, PRIVATE_KEY)).resolves.toBe(`${message}.AQID`);
        expect(mockSign).toHaveBeenCalledWith(
            {
                hash: 'SHA-256',
                name: 'ECDSA',
            },
            PRIVATE_KEY,
            new TextEncoder().encode(message),
        );
    });
});
