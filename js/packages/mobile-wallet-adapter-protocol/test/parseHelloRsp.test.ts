import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ENCODED_PUBLIC_KEY_LENGTH_BYTES } from '../src/encryptedMessage.js';
import parseHelloRsp from '../src/parseHelloRsp.js';

const AES_KEY = {} as CryptoKey;
const ASSOCIATION_PUBLIC_KEY = {} as CryptoKey;
const ASSOCIATION_PUBLIC_KEY_BYTES = Uint8Array.of(4, 5, 6);
const ECDH_PRIVATE_KEY = {} as CryptoKey;
const ECDH_SECRET_KEY = {} as CryptoKey;
const PAYLOAD_BUFFER = Uint8Array.from({ length: ENCODED_PUBLIC_KEY_LENGTH_BYTES + 1 }, (_, index) => index).buffer;
const SHARED_SECRET_BITS = Uint8Array.of(1, 2, 3).buffer;
const WALLET_PUBLIC_KEY = {} as CryptoKey;

const { mockDeriveBits, mockDeriveKey, mockExportKey, mockImportKey } = vi.hoisted(() => ({
    mockDeriveBits: vi.fn(),
    mockDeriveKey: vi.fn(),
    mockExportKey: vi.fn(),
    mockImportKey: vi.fn(),
}));

beforeEach(() => {
    mockDeriveBits.mockResolvedValue(SHARED_SECRET_BITS);
    mockDeriveKey.mockResolvedValue(AES_KEY);
    mockExportKey.mockResolvedValue(ASSOCIATION_PUBLIC_KEY_BYTES.buffer);
    mockImportKey.mockResolvedValueOnce(WALLET_PUBLIC_KEY).mockResolvedValueOnce(ECDH_SECRET_KEY);
    vi.stubGlobal('crypto', {
        subtle: {
            deriveBits: mockDeriveBits,
            deriveKey: mockDeriveKey,
            exportKey: mockExportKey,
            importKey: mockImportKey,
        },
    });
});

afterEach(() => {
    mockDeriveBits.mockReset();
    mockDeriveKey.mockReset();
    mockExportKey.mockReset();
    mockImportKey.mockReset();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
});

describe('parseHelloRsp', () => {
    it('derives a shared AES-GCM key from the wallet public key and association public key', async () => {
        await expect(parseHelloRsp(PAYLOAD_BUFFER, ASSOCIATION_PUBLIC_KEY, ECDH_PRIVATE_KEY)).resolves.toBe(AES_KEY);

        expect(mockExportKey).toHaveBeenCalledWith('raw', ASSOCIATION_PUBLIC_KEY);
        expect(mockImportKey).toHaveBeenNthCalledWith(
            1,
            'raw',
            expect.any(ArrayBuffer),
            {
                name: 'ECDH',
                namedCurve: 'P-256',
            },
            false,
            [],
        );
        expect(new Uint8Array(mockImportKey.mock.calls[0][1] as ArrayBuffer)).toEqual(
            new Uint8Array(PAYLOAD_BUFFER.slice(0, ENCODED_PUBLIC_KEY_LENGTH_BYTES)),
        );
        expect(mockDeriveBits).toHaveBeenCalledWith(
            {
                name: 'ECDH',
                public: WALLET_PUBLIC_KEY,
            },
            ECDH_PRIVATE_KEY,
            256,
        );
        expect(mockImportKey).toHaveBeenNthCalledWith(2, 'raw', SHARED_SECRET_BITS, 'HKDF', false, ['deriveKey']);
        expect(mockDeriveKey).toHaveBeenCalledWith(
            {
                hash: 'SHA-256',
                info: new Uint8Array(),
                name: 'HKDF',
                salt: ASSOCIATION_PUBLIC_KEY_BYTES,
            },
            ECDH_SECRET_KEY,
            {
                length: 128,
                name: 'AES-GCM',
            },
            false,
            ['encrypt', 'decrypt'],
        );
    });
});
