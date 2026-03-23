// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import {
    getIsLocalAssociationSupported,
    getIsPwaLaunchedAsApp,
    getIsRemoteAssociationSupported,
    isWebView,
} from '../src/getIsSupported';

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
    vi.restoreAllMocks();
});

describe('getIsSupported helpers', () => {
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
});

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
    userAgent = DESKTOP_BROWSER_USER_AGENT,
}: {
    displayMode?: {
        fullscreen?: boolean;
        minimalUI?: boolean;
        standalone?: boolean;
    };
    documentReferrer?: string;
    isSecureContext?: boolean;
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
