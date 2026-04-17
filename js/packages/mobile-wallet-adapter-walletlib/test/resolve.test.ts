import { afterEach, describe, expect, it, vi } from 'vitest';

const { mockResolve, nativeModules, platform } = vi.hoisted(() => ({
    mockResolve: vi.fn(),
    nativeModules: {
        SolanaMobileWalletAdapterWalletLib: {
            resolve: vi.fn(),
        },
    },
    platform: { OS: 'android' },
}));

nativeModules.SolanaMobileWalletAdapterWalletLib.resolve = mockResolve;

vi.mock('react-native', () => ({
    NativeModules: nativeModules,
    Platform: platform,
}));

import { type AuthorizeDappRequest, type AuthorizeDappResponse, MWARequestType, resolve } from '../src/resolve.js';

afterEach(() => {
    mockResolve.mockReset();
    platform.OS = 'android';
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
});
