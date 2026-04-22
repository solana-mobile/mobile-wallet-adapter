import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import generateAssociationKeypair from '../src/generateAssociationKeypair.js';

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

describe('generateAssociationKeypair', () => {
    it('generates a non-extractable ECDSA P-256 keypair for signing', async () => {
        await expect(generateAssociationKeypair()).resolves.toBe(KEYPAIR);
        expect(mockGenerateKey).toHaveBeenCalledWith(
            {
                name: 'ECDSA',
                namedCurve: 'P-256',
            },
            false,
            ['sign'],
        );
    });
});
