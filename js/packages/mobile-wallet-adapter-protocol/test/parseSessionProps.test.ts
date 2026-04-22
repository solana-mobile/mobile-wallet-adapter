import { afterEach, describe, expect, it, vi } from 'vitest';

const { mockDecryptMessage } = vi.hoisted(() => ({
    mockDecryptMessage: vi.fn(),
}));

vi.mock('../src/encryptedMessage.js', () => ({
    decryptMessage: mockDecryptMessage,
}));

import { SolanaMobileWalletAdapterErrorCode } from '../src/errors.js';
import parseSessionProps from '../src/parseSessionProps.js';

const MESSAGE = Uint8Array.of(1, 2, 3).buffer;
const SHARED_SECRET = {} as CryptoKey;

afterEach(() => {
    mockDecryptMessage.mockReset();
    vi.restoreAllMocks();
});

describe('parseSessionProps', () => {
    it('defaults to the legacy protocol version when the version field is absent', async () => {
        mockDecryptMessage.mockResolvedValue(JSON.stringify({}));

        await expect(parseSessionProps(MESSAGE, SHARED_SECRET)).resolves.toEqual({
            protocol_version: 'legacy',
        });
        expect(mockDecryptMessage).toHaveBeenCalledWith(MESSAGE, SHARED_SECRET);
    });

    it('parses the legacy protocol version string', async () => {
        mockDecryptMessage.mockResolvedValue(JSON.stringify({ v: 'legacy' }));

        await expect(parseSessionProps(MESSAGE, SHARED_SECRET)).resolves.toEqual({
            protocol_version: 'legacy',
        });
    });

    it('parses the numeric v1 protocol version', async () => {
        mockDecryptMessage.mockResolvedValue(JSON.stringify({ v: 1 }));

        await expect(parseSessionProps(MESSAGE, SHARED_SECRET)).resolves.toEqual({
            protocol_version: 'v1',
        });
    });

    it('parses the string v1 protocol version', async () => {
        mockDecryptMessage.mockResolvedValue(JSON.stringify({ v: '1' }));

        await expect(parseSessionProps(MESSAGE, SHARED_SECRET)).resolves.toEqual({
            protocol_version: 'v1',
        });
    });

    it('parses the v1 protocol version literal', async () => {
        mockDecryptMessage.mockResolvedValue(JSON.stringify({ v: 'v1' }));

        await expect(parseSessionProps(MESSAGE, SHARED_SECRET)).resolves.toEqual({
            protocol_version: 'v1',
        });
    });

    it('rejects unsupported protocol versions', async () => {
        mockDecryptMessage.mockResolvedValue(JSON.stringify({ v: 'v2' }));

        await expect(parseSessionProps(MESSAGE, SHARED_SECRET)).rejects.toEqual(
            expect.objectContaining({
                code: SolanaMobileWalletAdapterErrorCode.ERROR_INVALID_PROTOCOL_VERSION,
                name: 'SolanaMobileWalletAdapterError',
            }),
        );
    });
});
