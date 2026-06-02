import { afterEach, describe, expect, it, vi } from 'vitest';

const {
    mockGetCallingPackage,
    mockGetCallingPackageUid,
    mockGetUidForPackage,
    mockVerifyCallingPackage,
    nativeModules,
    nativeDigitalAssetLinks,
    platform,
} = vi.hoisted(() => {
    const mockGetCallingPackage = vi.fn();
    const mockGetCallingPackageUid = vi.fn();
    const mockGetUidForPackage = vi.fn();
    const mockVerifyCallingPackage = vi.fn();
    const nativeDigitalAssetLinks = {
        getCallingPackage: vi.fn(),
        getCallingPackageUid: vi.fn(),
        getUidForPackage: vi.fn(),
        verifyCallingPackage: vi.fn(),
    };
    nativeDigitalAssetLinks.getCallingPackage = mockGetCallingPackage;
    nativeDigitalAssetLinks.getCallingPackageUid = mockGetCallingPackageUid;
    nativeDigitalAssetLinks.getUidForPackage = mockGetUidForPackage;
    nativeDigitalAssetLinks.verifyCallingPackage = mockVerifyCallingPackage;

    return {
        mockGetCallingPackage,
        mockGetCallingPackageUid,
        mockGetUidForPackage,
        mockVerifyCallingPackage,
        nativeDigitalAssetLinks,
        nativeModules: {
            SolanaMobileDigitalAssetLinks: nativeDigitalAssetLinks as
                | {
                      getCallingPackage: ReturnType<typeof vi.fn>;
                      getCallingPackageUid: ReturnType<typeof vi.fn>;
                      getUidForPackage: ReturnType<typeof vi.fn>;
                      verifyCallingPackage: ReturnType<typeof vi.fn>;
                  }
                | undefined,
        },
        platform: { OS: 'android' },
    };
});

vi.mock('react-native', () => ({
    NativeModules: nativeModules,
    Platform: platform,
}));

import {
    getCallingPackage,
    getCallingPackageUid,
    getUidForPackage,
    verifyCallingPackage,
} from '../src/useDigitalAssetLinks.js';

afterEach(() => {
    mockGetCallingPackage.mockReset();
    mockGetCallingPackageUid.mockReset();
    mockGetUidForPackage.mockReset();
    mockVerifyCallingPackage.mockReset();
    nativeModules.SolanaMobileDigitalAssetLinks = nativeDigitalAssetLinks;
    platform.OS = 'android';
    vi.resetModules();
});

describe('useDigitalAssetLinks helpers', () => {
    it('forwards getCallingPackage to the native module', async () => {
        mockGetCallingPackage.mockResolvedValue('com.example.wallet');

        await expect(getCallingPackage()).resolves.toBe('com.example.wallet');
        expect(mockGetCallingPackage).toHaveBeenCalledWith();
    });

    it('forwards getCallingPackageUid to the native module', async () => {
        mockGetCallingPackageUid.mockResolvedValue(42);

        await expect(getCallingPackageUid()).resolves.toBe(42);
        expect(mockGetCallingPackageUid).toHaveBeenCalledWith();
    });

    it('forwards getUidForPackage to the native module', async () => {
        mockGetUidForPackage.mockResolvedValue(42);

        await expect(getUidForPackage('com.example.wallet')).resolves.toBe(42);
        expect(mockGetUidForPackage).toHaveBeenCalledWith('com.example.wallet');
    });

    it('forwards verifyCallingPackage to the native module', async () => {
        mockVerifyCallingPackage.mockResolvedValue(true);

        await expect(verifyCallingPackage('https://example.com')).resolves.toBe(true);
        expect(mockVerifyCallingPackage).toHaveBeenCalledWith('https://example.com');
    });

    it('throws a linking error when the Android native module is missing', async () => {
        nativeModules.SolanaMobileDigitalAssetLinks = undefined;
        vi.resetModules();
        const { getCallingPackage } = await import('../src/useDigitalAssetLinks.js');

        await expect(getCallingPackage()).rejects.toThrow(
            "The package 'solana-mobile-wallet-adapter-walletlib' doesn't seem to be linked.",
        );
    });

    it('throws a platform error outside Android', async () => {
        platform.OS = 'ios';
        vi.resetModules();
        const { getCallingPackage } = await import('../src/useDigitalAssetLinks.js');

        await expect(getCallingPackage()).rejects.toThrow('is only compatible with React Native Android');
    });
});
