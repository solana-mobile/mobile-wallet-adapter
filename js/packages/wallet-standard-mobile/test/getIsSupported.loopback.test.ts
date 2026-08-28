// @vitest-environment jsdom
// @vitest-environment-options {"url": "http://localhost:5173/"}
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { checkLocalNetworkAccessPermission, isLocalWebSocketAvailable } from '../src/getIsSupported.js';

const ANDROID_BROWSER_USER_AGENT =
    'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Mobile Safari/537.36';

// A page served from a loopback origin (the standard `adb reverse` localhost dev
// workflow) is exempt from Local Network Access gating: the browser never shows a
// prompt, so the permission state can never leave "prompt". Both gates must bypass
// the permission query entirely or the connection flow deadlocks until timeout.
describe('getIsSupported on a loopback origin', () => {
    let permissionQuery: ReturnType<typeof vi.fn>;

    beforeEach(() => {
        permissionQuery = vi.fn().mockResolvedValue({ onchange: null, state: 'prompt' });
        Object.defineProperty(navigator, 'permissions', {
            configurable: true,
            value: { query: permissionQuery },
        });
        Object.defineProperty(navigator, 'userAgent', {
            configurable: true,
            value: ANDROID_BROWSER_USER_AGENT,
        });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('skips the local network access permission check without querying permissions', async () => {
        expect(window.location.hostname).toBe('localhost');

        await expect(checkLocalNetworkAccessPermission()).resolves.toBeUndefined();
        expect(permissionQuery).not.toHaveBeenCalled();
    });

    it('treats the local web socket as available without querying permissions', async () => {
        await expect(isLocalWebSocketAvailable()).resolves.toBe(true);
        expect(permissionQuery).not.toHaveBeenCalled();
    });
});
