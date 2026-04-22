// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';

const { ErrorModalMock, errorModalInstances, resetErrorModalMocks } = vi.hoisted(() => {
    type ErrorModalInstance = {
        initWithError: ReturnType<typeof vi.fn>;
        open: ReturnType<typeof vi.fn>;
    };

    const errorModalInstances: ErrorModalInstance[] = [];

    class ErrorModalMock {
        initWithError = vi.fn();
        open = vi.fn();

        constructor() {
            errorModalInstances.push(this);
        }
    }

    const resetErrorModalMocks = () => {
        errorModalInstances.length = 0;
    };

    return {
        ErrorModalMock,
        errorModalInstances,
        resetErrorModalMocks,
    };
});

vi.mock('../src/embedded-modal/errorModal.js', () => ({
    default: ErrorModalMock,
}));

vi.mock('../src/wallet.js', () => ({
    SolanaMobileWalletAdapterWallet: class SolanaMobileWalletAdapterWallet {},
}));

import createDefaultWalletNotFoundHandler, {
    defaultErrorModalWalletNotFoundHandler,
} from '../src/createDefaultWalletNotFoundHandler.js';

const ANDROID_WEBVIEW_USER_AGENT =
    'Mozilla/5.0 (Linux; Android 14; Pixel 8 Build/UP1A.231005.007; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/123.0.0.0 Mobile Safari/537.36';
const DESKTOP_BROWSER_USER_AGENT =
    'Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36';

afterEach(() => {
    resetErrorModalMocks();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
});

describe('createDefaultWalletNotFoundHandler', () => {
    it('does nothing when the window object is unavailable', async () => {
        vi.stubGlobal('window', undefined);

        await expect(defaultErrorModalWalletNotFoundHandler()).resolves.toBeUndefined();

        expect(errorModalInstances).toHaveLength(0);
    });

    it('shows the browser-not-supported error in Android WebViews', async () => {
        installBrowserGlobals(ANDROID_WEBVIEW_USER_AGENT);

        await expect(defaultErrorModalWalletNotFoundHandler()).resolves.toBeUndefined();

        expect(errorModalInstances).toHaveLength(1);
        expect(errorModalInstances[0].initWithError).toHaveBeenCalledWith({
            code: 'ERROR_BROWSER_NOT_SUPPORTED',
            message: '',
            name: 'SolanaMobileWalletAdapterError',
        });
        expect(errorModalInstances[0].open).toHaveBeenCalledTimes(1);
    });

    it('shows the wallet-not-found error in regular browsers', async () => {
        installBrowserGlobals(DESKTOP_BROWSER_USER_AGENT);

        await expect(defaultErrorModalWalletNotFoundHandler()).resolves.toBeUndefined();

        expect(errorModalInstances).toHaveLength(1);
        expect(errorModalInstances[0].initWithError).toHaveBeenCalledWith({
            code: 'ERROR_WALLET_NOT_FOUND',
            message: '',
            name: 'SolanaMobileWalletAdapterError',
        });
        expect(errorModalInstances[0].open).toHaveBeenCalledTimes(1);
    });

    it('returns a handler that delegates to the default modal flow', async () => {
        installBrowserGlobals(DESKTOP_BROWSER_USER_AGENT);

        const handler = createDefaultWalletNotFoundHandler();

        await expect(handler({} as Parameters<typeof handler>[0])).resolves.toBeUndefined();

        expect(errorModalInstances).toHaveLength(1);
        expect(errorModalInstances[0].initWithError).toHaveBeenCalledWith({
            code: 'ERROR_WALLET_NOT_FOUND',
            message: '',
            name: 'SolanaMobileWalletAdapterError',
        });
        expect(errorModalInstances[0].open).toHaveBeenCalledTimes(1);
    });
});

function installBrowserGlobals(userAgent: string) {
    Object.defineProperty(navigator, 'userAgent', {
        configurable: true,
        value: userAgent,
    });
}
