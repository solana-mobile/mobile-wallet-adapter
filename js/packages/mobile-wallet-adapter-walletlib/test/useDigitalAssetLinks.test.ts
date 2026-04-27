import { afterEach, describe, expect, it, vi } from 'vitest';

const {
    mockGetCallingPackage,
    mockGetCallingPackageUid,
    mockGetUidForPackage,
    mockVerifyCallingPackage,
    nativeModules,
    platform,
} = vi.hoisted(() => ({
    mockGetCallingPackage: vi.fn(),
    mockGetCallingPackageUid: vi.fn(),
    mockGetUidForPackage: vi.fn(),
    mockVerifyCallingPackage: vi.fn(),
    nativeModules: {
        SolanaMobileDigitalAssetLinks: {
            getCallingPackage: vi.fn(),
            getCallingPackageUid: vi.fn(),
            getUidForPackage: vi.fn(),
            verifyCallingPackage: vi.fn(),
        },
    },
    platform: { OS: 'android' },
}));

nativeModules.SolanaMobileDigitalAssetLinks.getCallingPackage = mockGetCallingPackage;
nativeModules.SolanaMobileDigitalAssetLinks.getCallingPackageUid = mockGetCallingPackageUid;
nativeModules.SolanaMobileDigitalAssetLinks.getUidForPackage = mockGetUidForPackage;
nativeModules.SolanaMobileDigitalAssetLinks.verifyCallingPackage = mockVerifyCallingPackage;

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
    platform.OS = 'android';
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
});
