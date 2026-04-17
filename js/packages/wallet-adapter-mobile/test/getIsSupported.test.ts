// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import getIsSupported from '../src/getIsSupported.js';

const ANDROID_BROWSER_USER_AGENT =
    'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Mobile Safari/537.36';
const DESKTOP_BROWSER_USER_AGENT =
    'Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36';

beforeEach(() => {
    installBrowserGlobals();
});

afterEach(() => {
    vi.restoreAllMocks();
});

describe('getIsSupported', () => {
    it('returns true for secure Android browsers', () => {
        installBrowserGlobals({
            isSecureContext: true,
            userAgent: ANDROID_BROWSER_USER_AGENT,
        });

        expect(getIsSupported()).toBe(true);
    });

    it('returns false for insecure Android browsers', () => {
        installBrowserGlobals({
            isSecureContext: false,
            userAgent: ANDROID_BROWSER_USER_AGENT,
        });

        expect(getIsSupported()).toBe(false);
    });

    it('returns false for non-Android browsers', () => {
        installBrowserGlobals({
            isSecureContext: true,
            userAgent: DESKTOP_BROWSER_USER_AGENT,
        });

        expect(getIsSupported()).toBe(false);
    });
});

function installBrowserGlobals({
    isSecureContext = true,
    userAgent = DESKTOP_BROWSER_USER_AGENT,
}: {
    isSecureContext?: boolean;
    userAgent?: string;
} = {}) {
    Object.defineProperty(navigator, 'userAgent', {
        configurable: true,
        value: userAgent,
    });
    Object.defineProperty(window, 'isSecureContext', {
        configurable: true,
        value: isSecureContext,
    });
}
