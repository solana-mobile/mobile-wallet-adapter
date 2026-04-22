// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { SolanaMobileWalletAdapterErrorCode } from '../src/errors.js';
import getAssociateAndroidIntentURL, {
    getRemoteAssociateAndroidIntentURL,
} from '../src/getAssociateAndroidIntentURL.js';

const ASSOCIATION_PUBLIC_KEY = {} as CryptoKey;
const EXPORTED_KEY_BYTES = Uint8Array.of(251, 255);
const REFLECTOR_ID = Uint8Array.of(251, 255);

const { mockExportKey } = vi.hoisted(() => ({
    mockExportKey: vi.fn(),
}));

beforeEach(() => {
    mockExportKey.mockResolvedValue(EXPORTED_KEY_BYTES.buffer);
    vi.stubGlobal('crypto', {
        subtle: {
            exportKey: mockExportKey,
        },
    });
});

afterEach(() => {
    mockExportKey.mockReset();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
});

describe('getAssociateAndroidIntentURL', () => {
    it('creates a local association URL with the default wallet scheme', async () => {
        const url = await getAssociateAndroidIntentURL(ASSOCIATION_PUBLIC_KEY, 49152);

        expect(mockExportKey).toHaveBeenCalledWith('raw', ASSOCIATION_PUBLIC_KEY);
        expect(url.pathname).toBe('/v1/associate/local');
        expect(url.protocol).toBe('solana-wallet:');
        expect(url.searchParams.get('association')).toBe('-_8.');
        expect(url.searchParams.get('port')).toBe('49152');
        expect(url.searchParams.get('v')).toBe('v1');
    });

    it('creates a remote association URL with reflector metadata', async () => {
        const url = await getRemoteAssociateAndroidIntentURL(
            ASSOCIATION_PUBLIC_KEY,
            'reflector.example',
            REFLECTOR_ID,
            'https://wallet.example',
        );

        expect(mockExportKey).toHaveBeenCalledWith('raw', ASSOCIATION_PUBLIC_KEY);
        expect(url.host).toBe('wallet.example');
        expect(url.pathname).toBe('/v1/associate/remote');
        expect(url.searchParams.get('association')).toBe('-_8.');
        expect(url.searchParams.get('id')).toBe('-_8');
        expect(url.searchParams.get('reflector')).toBe('reflector.example');
        expect(url.searchParams.get('v')).toBe('v1');
    });

    it('creates a local association URL with an https wallet base URL', async () => {
        const url = await getAssociateAndroidIntentURL(ASSOCIATION_PUBLIC_KEY, 61234, 'https://wallet.example');

        expect(url.host).toBe('wallet.example');
        expect(url.pathname).toBe('/v1/associate/local');
        expect(url.protocol).toBe('https:');
        expect(url.searchParams.get('port')).toBe('61234');
    });

    it('rejects wallet base URLs that are not https', async () => {
        await expect(
            getAssociateAndroidIntentURL(ASSOCIATION_PUBLIC_KEY, 49152, 'http://wallet.example'),
        ).rejects.toEqual(
            expect.objectContaining({
                code: SolanaMobileWalletAdapterErrorCode.ERROR_FORBIDDEN_WALLET_BASE_URL,
                name: 'SolanaMobileWalletAdapterError',
            }),
        );
    });
});
