import { SOLANA_MAINNET_CHAIN } from '@solana/wallet-standard-chains';
import { afterEach, describe, expect, it, vi } from 'vitest';

const {
    LocalWalletMock,
    RemoteWalletMock,
    mockGetIsLocalAssociationSupported,
    mockGetIsRemoteAssociationSupported,
    mockIsSolanaMobileWebShell,
    mockIsWebView,
    mockLocalWalletConstructor,
    mockRegisterWallet,
    mockRemoteWalletConstructor,
} = vi.hoisted(() => {
    const mockGetIsLocalAssociationSupported = vi.fn();
    const mockGetIsRemoteAssociationSupported = vi.fn();
    const mockIsSolanaMobileWebShell = vi.fn();
    const mockIsWebView = vi.fn();
    const mockLocalWalletConstructor = vi.fn();
    const mockRegisterWallet = vi.fn();
    const mockRemoteWalletConstructor = vi.fn();

    class LocalWalletMock {
        constructor(config: unknown) {
            mockLocalWalletConstructor(config);
        }
    }

    class RemoteWalletMock {
        constructor(config: unknown) {
            mockRemoteWalletConstructor(config);
        }
    }

    return {
        LocalWalletMock,
        RemoteWalletMock,
        mockGetIsLocalAssociationSupported,
        mockGetIsRemoteAssociationSupported,
        mockIsSolanaMobileWebShell,
        mockIsWebView,
        mockLocalWalletConstructor,
        mockRegisterWallet,
        mockRemoteWalletConstructor,
    };
});

vi.mock('@wallet-standard/wallet', () => ({
    registerWallet: mockRegisterWallet,
}));

vi.mock('../src/getIsSupported.js', () => ({
    getIsLocalAssociationSupported: mockGetIsLocalAssociationSupported,
    getIsRemoteAssociationSupported: mockGetIsRemoteAssociationSupported,
    isSolanaMobileWebShell: mockIsSolanaMobileWebShell,
    isWebView: mockIsWebView,
}));

vi.mock('../src/wallet.js', () => ({
    LocalSolanaMobileWalletAdapterWallet: LocalWalletMock,
    RemoteSolanaMobileWalletAdapterWallet: RemoteWalletMock,
    SolanaMobileWalletAdapterWallet: class SolanaMobileWalletAdapterWallet {},
}));

import { registerMwa } from '../src/initialize.js';

afterEach(() => {
    mockGetIsLocalAssociationSupported.mockReset();
    mockGetIsRemoteAssociationSupported.mockReset();
    mockIsSolanaMobileWebShell.mockReset();
    mockIsWebView.mockReset();
    mockLocalWalletConstructor.mockReset();
    mockRegisterWallet.mockReset();
    mockRemoteWalletConstructor.mockReset();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
});

describe('registerMwa', () => {
    it('registers a local wallet outside WebViews', () => {
        const config = createConfig();
        const userAgent = 'Mozilla/5.0 (Linux; Android 14; Pixel 8)';

        installBrowserGlobals({ isSecureContext: true, userAgent });
        mockGetIsLocalAssociationSupported.mockReturnValue(true);
        mockIsWebView.mockReturnValue(false);

        registerMwa(config);

        expect(mockGetIsLocalAssociationSupported).toHaveBeenCalledTimes(1);
        expect(mockGetIsRemoteAssociationSupported).not.toHaveBeenCalled();
        expect(mockIsSolanaMobileWebShell).not.toHaveBeenCalled();
        expect(mockIsWebView).toHaveBeenCalledWith(userAgent);
        expect(mockLocalWalletConstructor).toHaveBeenCalledWith(config);
        expect(mockRemoteWalletConstructor).not.toHaveBeenCalled();
        expect(mockRegisterWallet).toHaveBeenCalledWith(expect.any(LocalWalletMock));
    });

    it('registers a local wallet inside the Solana Mobile Web Shell', () => {
        const config = createConfig();
        const userAgent = 'Mozilla/5.0 (Linux; Android 14; Pixel 8; wv) Solana Mobile Web Shell';

        installBrowserGlobals({ isSecureContext: true, userAgent });
        mockGetIsLocalAssociationSupported.mockReturnValue(true);
        mockIsSolanaMobileWebShell.mockReturnValue(true);
        mockIsWebView.mockReturnValue(true);

        registerMwa(config);

        expect(mockGetIsLocalAssociationSupported).toHaveBeenCalledTimes(1);
        expect(mockGetIsRemoteAssociationSupported).not.toHaveBeenCalled();
        expect(mockIsSolanaMobileWebShell).toHaveBeenCalledWith(userAgent);
        expect(mockIsWebView).toHaveBeenCalledWith(userAgent);
        expect(mockLocalWalletConstructor).toHaveBeenCalledWith(config);
        expect(mockRemoteWalletConstructor).not.toHaveBeenCalled();
        expect(mockRegisterWallet).toHaveBeenCalledWith(expect.any(LocalWalletMock));
    });

    it('registers a remote wallet when local association is unavailable in a WebView', () => {
        const config = createConfig({ remoteHostAuthority: 'remote.example.com' });
        const userAgent = 'Mozilla/5.0 (Linux; Android 14; Pixel 8; wv)';

        installBrowserGlobals({ isSecureContext: true, userAgent });
        mockGetIsLocalAssociationSupported.mockReturnValue(true);
        mockGetIsRemoteAssociationSupported.mockReturnValue(true);
        mockIsSolanaMobileWebShell.mockReturnValue(false);
        mockIsWebView.mockReturnValue(true);

        registerMwa(config);

        expect(mockGetIsLocalAssociationSupported).toHaveBeenCalledTimes(1);
        expect(mockGetIsRemoteAssociationSupported).toHaveBeenCalledTimes(1);
        expect(mockIsSolanaMobileWebShell).toHaveBeenCalledWith(userAgent);
        expect(mockIsWebView).toHaveBeenCalledWith(userAgent);
        expect(mockLocalWalletConstructor).not.toHaveBeenCalled();
        expect(mockRemoteWalletConstructor).toHaveBeenCalledWith(config);
        expect(mockRegisterWallet).toHaveBeenCalledWith(expect.any(RemoteWalletMock));
    });

    it('skips registration when remote association lacks a host authority', () => {
        const config = createConfig();
        const userAgent = 'Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0)';

        installBrowserGlobals({ isSecureContext: true, userAgent });
        mockGetIsLocalAssociationSupported.mockReturnValue(false);
        mockGetIsRemoteAssociationSupported.mockReturnValue(true);

        registerMwa(config);

        expect(mockGetIsLocalAssociationSupported).toHaveBeenCalledTimes(1);
        expect(mockGetIsRemoteAssociationSupported).toHaveBeenCalledTimes(1);
        expect(mockIsSolanaMobileWebShell).not.toHaveBeenCalled();
        expect(mockIsWebView).not.toHaveBeenCalled();
        expect(mockLocalWalletConstructor).not.toHaveBeenCalled();
        expect(mockRemoteWalletConstructor).not.toHaveBeenCalled();
        expect(mockRegisterWallet).not.toHaveBeenCalled();
    });

    it('warns and returns when there is no window object', () => {
        const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});

        registerMwa(createConfig());

        expect(mockGetIsLocalAssociationSupported).not.toHaveBeenCalled();
        expect(mockRegisterWallet).not.toHaveBeenCalled();
        expect(warnSpy).toHaveBeenCalledWith('MWA not registered: no window object');
    });

    it('warns and returns when the context is insecure', () => {
        const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});

        installBrowserGlobals({
            isSecureContext: false,
            userAgent: 'Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0)',
        });

        registerMwa(createConfig());

        expect(mockGetIsLocalAssociationSupported).not.toHaveBeenCalled();
        expect(mockRegisterWallet).not.toHaveBeenCalled();
        expect(warnSpy).toHaveBeenCalledWith('MWA not registered: secure context required (https)');
    });
});

function createConfig({ remoteHostAuthority }: { remoteHostAuthority?: string } = {}): Parameters<
    typeof registerMwa
>[0] {
    return {
        appIdentity: {
            icon: 'favicon.ico',
            name: 'Example App',
            uri: 'https://example.test',
        },
        authorizationCache: {
            clear: vi.fn().mockResolvedValue(undefined),
            get: vi.fn().mockResolvedValue(undefined),
            set: vi.fn().mockResolvedValue(undefined),
        },
        chainSelector: {
            select: vi.fn().mockResolvedValue(SOLANA_MAINNET_CHAIN),
        },
        chains: [SOLANA_MAINNET_CHAIN],
        onWalletNotFound: vi.fn().mockResolvedValue(undefined),
        ...(remoteHostAuthority ? { remoteHostAuthority } : {}),
    };
}

function installBrowserGlobals({ isSecureContext, userAgent }: { isSecureContext: boolean; userAgent: string }) {
    vi.stubGlobal('navigator', { userAgent });
    vi.stubGlobal('window', { isSecureContext });
}
