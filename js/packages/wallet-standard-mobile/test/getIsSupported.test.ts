// @vitest-environment jsdom
import {
    SolanaMobileWalletAdapterError,
    SolanaMobileWalletAdapterErrorCode,
} from '@solana-mobile/mobile-wallet-adapter-protocol';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const {
    LocalConnectionModalMock,
    LoopbackPermissionBlockedModalMock,
    LoopbackPermissionModalMock,
    blockedModalInstances,
    localConnectionModalInstances,
    permissionModalInstances,
    resetModalMocks,
} = vi.hoisted(() => {
    type ModalEventListener = (event?: Event) => void;
    type MockModalInstance = {
        addEventListener: ReturnType<typeof vi.fn>;
        closeListener: ModalEventListener | undefined;
        init: ReturnType<typeof vi.fn>;
        open: ReturnType<typeof vi.fn>;
    };
    type MockLocalConnectionModalInstance = MockModalInstance & {
        callback: (() => Promise<void>) | undefined;
        initWithCallback: ReturnType<typeof vi.fn>;
    };

    const blockedModalInstances: MockModalInstance[] = [];
    const localConnectionModalInstances: MockLocalConnectionModalInstance[] = [];
    const permissionModalInstances: MockModalInstance[] = [];

    class LoopbackPermissionBlockedModalMock {
        addEventListener = vi.fn((_: string, listener: ModalEventListener) => {
            this.closeListener = listener;
        });
        closeListener: ModalEventListener | undefined;
        init = vi.fn();
        open = vi.fn();

        constructor() {
            blockedModalInstances.push(this);
        }
    }

    class LocalConnectionModalMock {
        addEventListener = vi.fn((_: string, listener: ModalEventListener) => {
            this.closeListener = listener;
        });
        callback: (() => Promise<void>) | undefined;
        closeListener: ModalEventListener | undefined;
        init = vi.fn();
        initWithCallback = vi.fn((callback: () => Promise<void>) => {
            this.callback = callback;
        });
        open = vi.fn();

        constructor() {
            localConnectionModalInstances.push(this);
        }
    }

    class LoopbackPermissionModalMock {
        addEventListener = vi.fn((_: string, listener: ModalEventListener) => {
            this.closeListener = listener;
        });
        closeListener: ModalEventListener | undefined;
        init = vi.fn();
        open = vi.fn();

        constructor() {
            permissionModalInstances.push(this);
        }
    }

    const resetModalMocks = () => {
        blockedModalInstances.length = 0;
        localConnectionModalInstances.length = 0;
        permissionModalInstances.length = 0;
    };

    return {
        blockedModalInstances,
        LocalConnectionModalMock,
        localConnectionModalInstances,
        LoopbackPermissionBlockedModalMock,
        LoopbackPermissionModalMock,
        permissionModalInstances,
        resetModalMocks,
    };
});

vi.mock('../src/embedded-modal/localConnectionModal.js', () => ({
    default: LocalConnectionModalMock,
}));

vi.mock('../src/embedded-modal/loopbackBlockedModal.js', () => ({
    default: LoopbackPermissionBlockedModalMock,
}));

vi.mock('../src/embedded-modal/loopbackPermissionModal.js', () => ({
    default: LoopbackPermissionModalMock,
}));

import {
    checkLocalNetworkAccessPermission,
    getIsLocalAssociationSupported,
    getIsPwaLaunchedAsApp,
    getIsRemoteAssociationSupported,
    isSolanaMobileWebShell,
    isWebView,
} from '../src/getIsSupported.js';

const ANDROID_BROWSER_USER_AGENT =
    'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Mobile Safari/537.36';
const ANDROID_WEBVIEW_USER_AGENT =
    'Mozilla/5.0 (Linux; Android 14; Pixel 8 Build/UP1A.231005.007; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/123.0.0.0 Mobile Safari/537.36';
const DESKTOP_BROWSER_USER_AGENT =
    'Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36';

beforeEach(() => {
    installBrowserGlobals();
});

afterEach(() => {
    resetModalMocks();
    vi.restoreAllMocks();
});

type MockPermissionStatus = {
    onchange: ((event: Event) => void) | null;
    state: PermissionState;
};

describe('getIsSupported helpers', () => {
    it('bypasses local network access permission checks in the Solana Mobile Web Shell', async () => {
        const permissionQuery = vi.fn();

        installBrowserGlobals({
            permissionQuery,
            userAgent: `${ANDROID_WEBVIEW_USER_AGENT} Solana Mobile Web Shell`,
        });

        expect(isSolanaMobileWebShell(navigator.userAgent)).toBe(true);
        await expect(checkLocalNetworkAccessPermission()).resolves.toBeUndefined();
        expect(permissionQuery).not.toHaveBeenCalled();
    });

    it('covers the local network access API missing case', async () => {
        installBrowserGlobals({
            permissionQuery: vi
                .fn()
                .mockRejectedValue(new TypeError('loopback-network is not a valid permission name')),
        });

        await expect(checkLocalNetworkAccessPermission()).resolves.toBeUndefined();
        expect(blockedModalInstances).toHaveLength(0);
        expect(localConnectionModalInstances).toHaveLength(0);
        expect(permissionModalInstances).toHaveLength(0);
    });

    it('covers the prompt flow cancelling before permission is granted', async () => {
        const permission = createPermissionStatus('prompt');

        installBrowserGlobals({
            permissionQuery: vi.fn().mockResolvedValue(permission),
        });

        const permissionPromise = checkLocalNetworkAccessPermission();

        await vi.waitFor(() => {
            expect(permissionModalInstances).toHaveLength(1);
        });

        const closeEvent = new Event('close');
        permissionModalInstances[0].closeListener?.(closeEvent);

        await expect(permissionPromise).rejects.toMatchObject({
            code: SolanaMobileWalletAdapterErrorCode.ERROR_ASSOCIATION_CANCELLED,
            data: { event: closeEvent },
            message: 'Wallet connection cancelled by user',
            name: 'SolanaMobileWalletAdapterError',
        });
        expect(localConnectionModalInstances).toHaveLength(0);
    });

    it('covers the prompt flow completing after permission is granted', async () => {
        const permission = createPermissionStatus('prompt');

        installBrowserGlobals({
            permissionQuery: vi.fn().mockResolvedValue(permission),
        });

        const permissionPromise = checkLocalNetworkAccessPermission();

        await vi.waitFor(() => {
            expect(permissionModalInstances).toHaveLength(1);
        });

        permission.state = 'granted';
        permission.onchange?.(new Event('change'));

        await vi.waitFor(() => {
            expect(localConnectionModalInstances).toHaveLength(1);
        });

        expect(permission.onchange).toBeNull();
        expect(permissionModalInstances[0].init).toHaveBeenCalledTimes(1);
        expect(permissionModalInstances[0].open).toHaveBeenCalledTimes(1);
        expect(localConnectionModalInstances[0].initWithCallback).toHaveBeenCalledTimes(1);
        expect(localConnectionModalInstances[0].open).toHaveBeenCalledTimes(1);
        expect(localConnectionModalInstances[0].callback).toBeDefined();

        await localConnectionModalInstances[0].callback?.();

        await expect(permissionPromise).resolves.toBeUndefined();
    });

    it('covers the prompt flow failing after the local connection step is cancelled', async () => {
        const permission = createPermissionStatus('prompt');

        installBrowserGlobals({
            permissionQuery: vi.fn().mockResolvedValue(permission),
        });

        const permissionPromise = checkLocalNetworkAccessPermission();

        await vi.waitFor(() => {
            expect(permissionModalInstances).toHaveLength(1);
        });

        permission.state = 'granted';
        permission.onchange?.(new Event('change'));

        await vi.waitFor(() => {
            expect(localConnectionModalInstances).toHaveLength(1);
        });

        const closeEvent = new Event('close');
        localConnectionModalInstances[0].closeListener?.(closeEvent);

        await expect(permissionPromise).rejects.toMatchObject({
            code: SolanaMobileWalletAdapterErrorCode.ERROR_ASSOCIATION_CANCELLED,
            data: { event: closeEvent },
            message: 'Wallet connection cancelled by user',
            name: 'SolanaMobileWalletAdapterError',
        });
    });

    it('covers the prompt flow retrying and then failing once permission is denied', async () => {
        const deniedPermission = createPermissionStatus('denied');
        const permissionQuery = vi.fn();
        const promptPermission = createPermissionStatus('prompt');
        permissionQuery.mockResolvedValueOnce(promptPermission).mockResolvedValueOnce(deniedPermission);

        installBrowserGlobals({
            permissionQuery,
        });

        const permissionPromise = checkLocalNetworkAccessPermission();

        await vi.waitFor(() => {
            expect(permissionModalInstances).toHaveLength(1);
        });

        promptPermission.state = 'denied';
        promptPermission.onchange?.(new Event('change'));

        await expect(permissionPromise).rejects.toMatchObject({
            code: SolanaMobileWalletAdapterErrorCode.ERROR_LOOPBACK_ACCESS_BLOCKED,
            message: 'Local Network Access permission denied',
            name: 'SolanaMobileWalletAdapterError',
        });
        expect(permissionQuery).toHaveBeenCalledTimes(2);
        expect(blockedModalInstances).toHaveLength(1);
    });

    it('detects Android local association support only in a secure context', () => {
        installBrowserGlobals({ isSecureContext: true, userAgent: ANDROID_BROWSER_USER_AGENT });
        expect(getIsLocalAssociationSupported()).toBe(true);

        installBrowserGlobals({ isSecureContext: false, userAgent: ANDROID_BROWSER_USER_AGENT });
        expect(getIsLocalAssociationSupported()).toBe(false);
    });

    it('detects desktop-only remote association support', () => {
        installBrowserGlobals({ isSecureContext: true, userAgent: DESKTOP_BROWSER_USER_AGENT });
        expect(getIsRemoteAssociationSupported()).toBe(true);

        installBrowserGlobals({ isSecureContext: true, userAgent: ANDROID_BROWSER_USER_AGENT });
        expect(getIsRemoteAssociationSupported()).toBe(false);
    });

    it('recognizes WebView user agents', () => {
        expect(isWebView(ANDROID_WEBVIEW_USER_AGENT)).toBe(true);
        expect(isWebView(ANDROID_BROWSER_USER_AGENT)).toBe(false);
    });

    it('recognizes Solana Mobile Web Shell user agents', () => {
        expect(isSolanaMobileWebShell(`${ANDROID_WEBVIEW_USER_AGENT} Solana Mobile Web Shell`)).toBe(true);
        expect(isSolanaMobileWebShell(ANDROID_BROWSER_USER_AGENT)).toBe(false);
    });

    it('returns false for local association support on non-Android browsers', () => {
        installBrowserGlobals({ isSecureContext: true, userAgent: DESKTOP_BROWSER_USER_AGENT });

        expect(getIsLocalAssociationSupported()).toBe(false);
    });

    it('returns false when a PWA is not launched as an app', () => {
        installBrowserGlobals({
            displayMode: {},
            documentReferrer: '',
            isSecureContext: true,
            userAgent: DESKTOP_BROWSER_USER_AGENT,
        });

        expect(getIsPwaLaunchedAsApp()).toBe(false);
    });

    it('treats Android TWA referrers as app launches', () => {
        installBrowserGlobals({
            displayMode: {},
            documentReferrer: 'android-app://com.example.wallet',
            isSecureContext: true,
            userAgent: ANDROID_BROWSER_USER_AGENT,
        });

        expect(getIsPwaLaunchedAsApp()).toBe(true);
    });

    it('treats matching display modes as app launches', () => {
        installBrowserGlobals({
            displayMode: { standalone: true },
            documentReferrer: '',
            isSecureContext: true,
            userAgent: DESKTOP_BROWSER_USER_AGENT,
        });

        expect(getIsPwaLaunchedAsApp()).toBe(true);
    });

    it('returns immediately when local network access is already granted', async () => {
        const permission = createPermissionStatus('granted');
        const permissionQuery = vi.fn().mockResolvedValue(permission);

        installBrowserGlobals({
            permissionQuery,
        });

        await expect(checkLocalNetworkAccessPermission()).resolves.toBeUndefined();
        expect(permissionQuery).toHaveBeenCalledWith({ name: 'loopback-network' });
        expect(blockedModalInstances).toHaveLength(0);
        expect(localConnectionModalInstances).toHaveLength(0);
        expect(permissionModalInstances).toHaveLength(0);
    });

    it('shows the blocked modal and throws when local network access is denied', async () => {
        const permission = createPermissionStatus('denied');

        installBrowserGlobals({
            permissionQuery: vi.fn().mockResolvedValue(permission),
        });

        let thrownError: unknown;

        try {
            await checkLocalNetworkAccessPermission();
        } catch (error) {
            thrownError = error;
        }

        expect(thrownError).toBeInstanceOf(SolanaMobileWalletAdapterError);
        expect(thrownError).toMatchObject({
            code: SolanaMobileWalletAdapterErrorCode.ERROR_LOOPBACK_ACCESS_BLOCKED,
            message: 'Local Network Access permission denied',
            name: 'SolanaMobileWalletAdapterError',
        });
        expect(blockedModalInstances).toHaveLength(1);
        expect(blockedModalInstances[0].init).toHaveBeenCalledTimes(1);
        expect(blockedModalInstances[0].open).toHaveBeenCalledTimes(1);
    });

    it('wraps unknown permission errors', async () => {
        installBrowserGlobals({
            permissionQuery: vi.fn().mockRejectedValue(new Error('permission service unavailable')),
        });

        await expect(checkLocalNetworkAccessPermission()).rejects.toMatchObject({
            code: SolanaMobileWalletAdapterErrorCode.ERROR_LOOPBACK_ACCESS_BLOCKED,
            message: 'permission service unavailable',
            name: 'SolanaMobileWalletAdapterError',
        });
    });

    it('wraps unknown permission states with the expected adapter error', async () => {
        installBrowserGlobals({
            permissionQuery: vi.fn().mockResolvedValue(createPermissionStatus('unknown' as PermissionState)),
        });

        await expect(checkLocalNetworkAccessPermission()).rejects.toMatchObject({
            code: SolanaMobileWalletAdapterErrorCode.ERROR_LOOPBACK_ACCESS_BLOCKED,
            message: 'Local Network Access permission unknown',
            name: 'SolanaMobileWalletAdapterError',
        });
    });
});

function createPermissionStatus(state: PermissionState): MockPermissionStatus {
    return {
        onchange: null,
        state,
    };
}

function createMatchMedia({
    fullscreen = false,
    minimalUI = false,
    standalone = false,
}: {
    fullscreen?: boolean;
    minimalUI?: boolean;
    standalone?: boolean;
} = {}) {
    return (query: string) => {
        switch (query) {
            case '(display-mode: fullscreen)':
                return { matches: fullscreen } as MediaQueryList;
            case '(display-mode: minimal-ui)':
                return { matches: minimalUI } as MediaQueryList;
            case '(display-mode: standalone)':
                return { matches: standalone } as MediaQueryList;
            default:
                return { matches: false } as MediaQueryList;
        }
    };
}

function installBrowserGlobals({
    displayMode,
    documentReferrer = '',
    isSecureContext = true,
    permissionQuery,
    userAgent = DESKTOP_BROWSER_USER_AGENT,
}: {
    displayMode?: {
        fullscreen?: boolean;
        minimalUI?: boolean;
        standalone?: boolean;
    };
    documentReferrer?: string;
    isSecureContext?: boolean;
    permissionQuery?: ReturnType<typeof vi.fn>;
    userAgent?: string;
} = {}) {
    Object.defineProperty(document, 'referrer', {
        configurable: true,
        value: documentReferrer,
    });
    Object.defineProperty(navigator, 'userAgent', {
        configurable: true,
        value: userAgent,
    });
    Object.defineProperty(navigator, 'permissions', {
        configurable: true,
        value: {
            query: permissionQuery ?? vi.fn(),
        },
    });
    Object.defineProperty(window, 'isSecureContext', {
        configurable: true,
        value: isSecureContext,
    });
    Object.defineProperty(window, 'matchMedia', {
        configurable: true,
        value: createMatchMedia(displayMode),
        writable: true,
    });
}
