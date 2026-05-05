import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { decryptMessage, encryptMessage } from '../src/encryptedMessage.js';

const CIPHERTEXT = Uint8Array.of(9, 10);
const INITIALIZATION_VECTOR = Uint8Array.of(11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22);
const PLAINTEXT = 'hello';
const SEQUENCE_NUMBER = 0x01020304;
const SEQUENCE_NUMBER_VECTOR = Uint8Array.of(1, 2, 3, 4);
const SHARED_SECRET = {} as CryptoKey;

const { mockDecrypt, mockEncrypt, mockGetRandomValues } = vi.hoisted(() => ({
    mockDecrypt: vi.fn(),
    mockEncrypt: vi.fn(),
    mockGetRandomValues: vi.fn(),
}));

beforeEach(() => {
    mockDecrypt.mockResolvedValue(new TextEncoder().encode(PLAINTEXT).buffer);
    mockEncrypt.mockResolvedValue(CIPHERTEXT.buffer);
    mockGetRandomValues.mockImplementation((array: Uint8Array) => {
        array.set(INITIALIZATION_VECTOR);
        return array;
    });
    vi.stubGlobal('crypto', {
        getRandomValues: mockGetRandomValues,
        subtle: {
            decrypt: mockDecrypt,
            encrypt: mockEncrypt,
        },
    });
});

afterEach(() => {
    mockDecrypt.mockReset();
    mockEncrypt.mockReset();
    mockGetRandomValues.mockReset();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
});

describe('encryptedMessage', () => {
    it('decrypts the message ciphertext with the sequence number as additional data', async () => {
        const message = Uint8Array.of(...SEQUENCE_NUMBER_VECTOR, ...INITIALIZATION_VECTOR, ...CIPHERTEXT).buffer;

        await expect(decryptMessage(message, SHARED_SECRET)).resolves.toBe(PLAINTEXT);

        expect(mockDecrypt).toHaveBeenCalledWith(
            {
                additionalData: message.slice(0, SEQUENCE_NUMBER_VECTOR.byteLength),
                iv: message.slice(
                    SEQUENCE_NUMBER_VECTOR.byteLength,
                    SEQUENCE_NUMBER_VECTOR.byteLength + INITIALIZATION_VECTOR.byteLength,
                ),
                name: 'AES-GCM',
                tagLength: 128,
            },
            SHARED_SECRET,
            message.slice(SEQUENCE_NUMBER_VECTOR.byteLength + INITIALIZATION_VECTOR.byteLength),
        );
    });

    it('encrypts the plaintext and prefixes the sequence number and initialization vector', async () => {
        await expect(encryptMessage(PLAINTEXT, SEQUENCE_NUMBER, SHARED_SECRET)).resolves.toEqual(
            Uint8Array.of(...SEQUENCE_NUMBER_VECTOR, ...INITIALIZATION_VECTOR, ...CIPHERTEXT),
        );

        expect(mockGetRandomValues).toHaveBeenCalledWith(expect.any(Uint8Array));
        expect(mockEncrypt).toHaveBeenCalledWith(
            {
                additionalData: SEQUENCE_NUMBER_VECTOR,
                iv: INITIALIZATION_VECTOR,
                name: 'AES-GCM',
                tagLength: 128,
            },
            SHARED_SECRET,
            new TextEncoder().encode(PLAINTEXT),
        );
    });
});
