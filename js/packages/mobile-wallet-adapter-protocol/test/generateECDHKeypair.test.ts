import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import generateECDHKeypair from '../src/generateECDHKeypair.js';

const KEYPAIR = {
    privateKey: {} as CryptoKey,
    publicKey: {} as CryptoKey,
} as const satisfies CryptoKeyPair;

const { mockGenerateKey } = vi.hoisted(() => ({
    mockGenerateKey: vi.fn(),
}));

beforeEach(() => {
    mockGenerateKey.mockResolvedValue(KEYPAIR);
    vi.stubGlobal('crypto', {
        subtle: {
            generateKey: mockGenerateKey,
        },
    });
});

afterEach(() => {
    mockGenerateKey.mockReset();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
});

describe('generateECDHKeypair', () => {
    it('generates a non-extractable ECDH P-256 keypair for derivation', async () => {
        await expect(generateECDHKeypair()).resolves.toBe(KEYPAIR);
        expect(mockGenerateKey).toHaveBeenCalledWith(
            {
                name: 'ECDH',
                namedCurve: 'P-256',
            },
            false,
            ['deriveKey', 'deriveBits'],
        );
    });
});
