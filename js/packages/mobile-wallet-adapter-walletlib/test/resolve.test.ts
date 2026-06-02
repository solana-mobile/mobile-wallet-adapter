import { afterEach, describe, expect, it, vi } from 'vitest';

const { mockResolve, nativeModules, nativeWalletLib, platform } = vi.hoisted(() => {
    const mockResolve = vi.fn();
    const nativeWalletLib = {
        resolve: vi.fn(),
    };
    nativeWalletLib.resolve = mockResolve;

    return {
        mockResolve,
        nativeModules: {
            SolanaMobileWalletAdapterWalletLib: nativeWalletLib as { resolve: ReturnType<typeof vi.fn> } | undefined,
        },
        nativeWalletLib,
        platform: { OS: 'android' },
    };
});

vi.mock('react-native', () => ({
    NativeModules: nativeModules,
    Platform: platform,
}));

import { type AuthorizeDappRequest, type AuthorizeDappResponse, MWARequestType, resolve } from '../src/resolve.js';

afterEach(() => {
    mockResolve.mockReset();
    nativeModules.SolanaMobileWalletAdapterWalletLib = nativeWalletLib;
    platform.OS = 'android';
    vi.resetModules();
});

describe('resolve', () => {
    it('serializes the request and response before forwarding them to the native module', () => {
        const request: AuthorizeDappRequest = {
            __type: MWARequestType.AuthorizeDappRequest,
            chain: 'solana:mainnet',
            requestId: 'request-1',
            sessionId: 'session-1',
        };
        const response: AuthorizeDappResponse = {
            accounts: [],
        };

        resolve(request, response);

        expect(mockResolve).toHaveBeenCalledWith(JSON.stringify(request), JSON.stringify(response));
    });

    it('throws a linking error when the Android native module is missing', async () => {
        nativeModules.SolanaMobileWalletAdapterWalletLib = undefined;
        vi.resetModules();
        const { resolve } = await import('../src/resolve.js');
        const request: AuthorizeDappRequest = {
            __type: MWARequestType.AuthorizeDappRequest,
            chain: 'solana:mainnet',
            requestId: 'request-1',
            sessionId: 'session-1',
        };
        const response: AuthorizeDappResponse = {
            accounts: [],
        };

        expect(() => resolve(request, response)).toThrow(
            "The package 'solana-mobile-wallet-adapter-walletlib' doesn't seem to be linked.",
        );
    });

    it('throws a platform error outside Android', async () => {
        platform.OS = 'ios';
        vi.resetModules();
        const { resolve } = await import('../src/resolve.js');
        const request: AuthorizeDappRequest = {
            __type: MWARequestType.AuthorizeDappRequest,
            chain: 'solana:mainnet',
            requestId: 'request-1',
            sessionId: 'session-1',
        };
        const response: AuthorizeDappResponse = {
            accounts: [],
        };

        expect(() => resolve(request, response)).toThrow('is only compatible with React Native Android');
    });
});
