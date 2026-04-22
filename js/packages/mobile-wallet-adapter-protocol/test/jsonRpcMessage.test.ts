import { afterEach, describe, expect, it, vi } from 'vitest';

const { mockDecryptMessage, mockEncryptMessage } = vi.hoisted(() => ({
    mockDecryptMessage: vi.fn(),
    mockEncryptMessage: vi.fn(),
}));

vi.mock('../src/encryptedMessage.js', () => ({
    decryptMessage: mockDecryptMessage,
    encryptMessage: mockEncryptMessage,
}));

import { SolanaMobileWalletAdapterProtocolError } from '../src/errors.js';
import { decryptJsonRpcMessage, encryptJsonRpcMessage } from '../src/jsonRpcMessage.js';

const MESSAGE = Uint8Array.of(1, 2, 3).buffer;
const SHARED_SECRET = {} as CryptoKey;

afterEach(() => {
    mockDecryptMessage.mockReset();
    mockEncryptMessage.mockReset();
    vi.restoreAllMocks();
});

describe('jsonRpcMessage', () => {
    it('encrypts a JSON-RPC request using the message id as the sequence number', async () => {
        const jsonRpcMessage = {
            id: 7,
            jsonrpc: '2.0' as const,
            method: 'authorize',
            params: { chain: 'solana:mainnet' },
        };
        const encrypted = Uint8Array.of(4, 5, 6);

        mockEncryptMessage.mockResolvedValue(encrypted);

        await expect(encryptJsonRpcMessage(jsonRpcMessage, SHARED_SECRET)).resolves.toBe(encrypted);
        expect(mockEncryptMessage).toHaveBeenCalledWith(JSON.stringify(jsonRpcMessage), 7, SHARED_SECRET);
    });

    it('decrypts a JSON-RPC result message', async () => {
        mockDecryptMessage.mockResolvedValue(
            JSON.stringify({
                id: 8,
                jsonrpc: '2.0',
                result: { auth_token: 'token' },
            }),
        );

        await expect(decryptJsonRpcMessage(MESSAGE, SHARED_SECRET)).resolves.toEqual({
            id: 8,
            jsonrpc: '2.0',
            result: { auth_token: 'token' },
        });
        expect(mockDecryptMessage).toHaveBeenCalledWith(MESSAGE, SHARED_SECRET);
    });

    it('throws a protocol error for JSON-RPC error responses', async () => {
        mockDecryptMessage.mockResolvedValue(
            JSON.stringify({
                error: {
                    code: -3,
                    message: 'not signed',
                },
                id: 9,
                jsonrpc: '2.0',
            }),
        );

        await expect(decryptJsonRpcMessage(MESSAGE, SHARED_SECRET)).rejects.toBeInstanceOf(
            SolanaMobileWalletAdapterProtocolError,
        );
        await expect(decryptJsonRpcMessage(MESSAGE, SHARED_SECRET)).rejects.toEqual(
            expect.objectContaining({
                code: -3,
                jsonRpcMessageId: 9,
                message: 'not signed',
                name: 'SolanaMobileWalletAdapterProtocolError',
            }),
        );
    });
});
