import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import createHelloReq from '../src/createHelloReq.js';

const ASSOCIATION_PRIVATE_KEY = {} as CryptoKey;
const ECDH_PUBLIC_KEY = {} as CryptoKey;
const EXPORTED_PUBLIC_KEY = Uint8Array.of(1, 2, 3);
const SIGNATURE = Uint8Array.of(4, 5, 6, 7);

const { mockExportKey, mockSign } = vi.hoisted(() => ({
    mockExportKey: vi.fn(),
    mockSign: vi.fn(),
}));

beforeEach(() => {
    mockExportKey.mockResolvedValue(EXPORTED_PUBLIC_KEY.buffer);
    mockSign.mockResolvedValue(SIGNATURE.buffer);
    vi.stubGlobal('crypto', {
        subtle: {
            exportKey: mockExportKey,
            sign: mockSign,
        },
    });
});

afterEach(() => {
    mockExportKey.mockReset();
    mockSign.mockReset();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
});

describe('createHelloReq', () => {
    it('concatenates the exported public key and signature', async () => {
        const helloReq = await createHelloReq(ECDH_PUBLIC_KEY, ASSOCIATION_PRIVATE_KEY);

        expect(mockExportKey).toHaveBeenCalledWith('raw', ECDH_PUBLIC_KEY);
        expect(mockSign).toHaveBeenCalledWith(
            { hash: 'SHA-256', name: 'ECDSA' },
            ASSOCIATION_PRIVATE_KEY,
            EXPORTED_PUBLIC_KEY.buffer,
        );
        expect(helloReq).toEqual(Uint8Array.of(1, 2, 3, 4, 5, 6, 7));
    });
});
