import {
    SolanaMobileWalletAdapterError,
    SolanaMobileWalletAdapterErrorCode,
} from '@solana-mobile/mobile-wallet-adapter-protocol';

import LocalConnectionModal from './embedded-modal/localConnectionModal.js';
import LoopbackPermissionBlockedModal from './embedded-modal/loopbackBlockedModal.js';
import LoopbackPermissionModal from './embedded-modal/loopbackPermissionModal.js';

export function getIsLocalAssociationSupported() {
    return (
        typeof window !== 'undefined' &&
        window.isSecureContext &&
        typeof document !== 'undefined' &&
        /android/i.test(navigator.userAgent)
    );
}

export function getIsRemoteAssociationSupported() {
    return (
        typeof window !== 'undefined' &&
        window.isSecureContext &&
        typeof document !== 'undefined' &&
        !/Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent)
    );
}

// Source: https://github.com/anza-xyz/wallet-adapter/blob/master/packages/core/react/src/getEnvironment.ts#L14
// This is the same implementation that gated MWA in the Anza wallet-adapter-react library.
export function isWebView(userAgentString: string) {
    return /(WebView|Version\/.+(Chrome)\/(\d+)\.(\d+)\.(\d+)\.(\d+)|; wv\).+(Chrome)\/(\d+)\.(\d+)\.(\d+)\.(\d+))/i.test(
        userAgentString,
    );
}

export function isSolanaMobileWebShell(userAgentString: string) {
    return userAgentString.includes('Solana Mobile Web Shell');
}

// Source: https://web.dev/learn/pwa/detection/
export function getIsPwaLaunchedAsApp() {
    // Check for Android TWA
    const isAndroidTwa = typeof document !== 'undefined' && document.referrer.startsWith('android-app://');

    // Check for display-mode: standalone, fullscreen, or minimal-ui
    if (typeof window == 'undefined') return isAndroidTwa;
    const isStandalone = window.matchMedia('(display-mode: standalone)').matches;
    const isFullscreen = window.matchMedia('(display-mode: fullscreen)').matches;
    const isMinimalUI = window.matchMedia('(display-mode: minimal-ui)').matches;

    // App mode if any of these conditions are true
    return isAndroidTwa || isStandalone || isFullscreen || isMinimalUI;
}

/**
 * Returns true when a hostname is a loopback host per the URL spec: `localhost`,
 * `*.localhost`, any 127.0.0.0/8 address, or `[::1]`. Hostnames from
 * `location.hostname` are already lowercased and normalized by the browser's URL parser.
 */
export function isLoopbackHost(hostname: string): boolean {
    return (
        hostname === 'localhost' ||
        hostname.endsWith('.localhost') ||
        hostname === '[::1]' ||
        /^127\.\d{1,3}\.\d{1,3}\.\d{1,3}$/.test(hostname)
    );
}

/** Returns true when the current page is served from a loopback origin. */
function isLoopbackOrigin(): boolean {
    return typeof window !== 'undefined' && !!window.location && isLoopbackHost(window.location.hostname);
}

/**
 * Returns true when a local websocket connection to the wallet can be attempted without
 * going through the Local Network Access permission flow: inside the Solana Mobile Web
 * Shell, on a loopback origin (exempt from LNA gating), or in browsers that don't
 * implement the LNA permission. Returns false when the permission exists but has not
 * been granted.
 */
export async function isLocalWebSocketAvailable(): Promise<boolean> {
    if (typeof navigator !== 'undefined' && isSolanaMobileWebShell(navigator.userAgent)) {
        return true;
    }
    if (isLoopbackOrigin()) {
        // Local Network Access only gates requests that cross into a more private
        // address space. A page served from a loopback origin (e.g. localhost dev
        // via `adb reverse`) fetching loopback is same-space and exempt, so the
        // permission state can never leave "prompt" — the browser will never show
        // its prompt. The permission is irrelevant here; connect directly.
        return true;
    }
    try {
        const permission: PermissionStatus = await navigator.permissions.query({
            name: 'loopback-network' as PermissionName,
        });
        return permission.state === 'granted';
    } catch (e) {
        if (
            e instanceof TypeError &&
            (e.message.includes('loopback-network') || e.message.includes('local-network-access'))
        ) {
            return true;
        }
        return false;
    }
}

/**
 * Ensures the Local Network Access permission is granted before local association,
 * walking the user through the permission prompt flow when the state is "prompt".
 * Resolves immediately when the permission is granted, irrelevant (Web Shell, loopback
 * origins), or unsupported by the browser. Throws a SolanaMobileWalletAdapterError when
 * the permission is denied or the user cancels.
 */
export async function checkLocalNetworkAccessPermission(): Promise<void> {
    if (typeof navigator !== 'undefined' && isSolanaMobileWebShell(navigator.userAgent)) {
        // Solana Mobile Web Shell runs inside an Android WebView hosted by a
        // native app, not a regular mobile browser tab. The WebView currently
        // does not expose a usable loopback-network permission query, so this
        // check throws even though the shell can still proceed with local
        // association. Keep this bypass scoped to the explicit Web Shell user
        // agent marker so the normal browser permission flow remains unchanged.
        return;
    }

    if (isLoopbackOrigin()) {
        // See isLocalWebSocketAvailable: loopback-origin pages are exempt from
        // Local Network Access gating, so there is nothing to prompt for. Showing
        // the permission modal here would deadlock — the state never changes.
        return;
    }

    try {
        const lnaPermission: PermissionStatus = await navigator.permissions.query({
            name: 'loopback-network' as PermissionName,
        });
        if (lnaPermission.state === 'granted') {
            // LNA permission already granted, continuing
            return;
        } else if (lnaPermission.state === 'denied') {
            // LNA permission denied, aborting
            const modal = new LoopbackPermissionBlockedModal();
            modal.init();
            modal.open();
            throw new SolanaMobileWalletAdapterError(
                SolanaMobileWalletAdapterErrorCode.ERROR_LOOPBACK_ACCESS_BLOCKED,
                'Local Network Access permission denied',
            );
        } else if (lnaPermission.state === 'prompt') {
            // Show permission explainer to user, and wait for the permission to change
            const modal = new LoopbackPermissionModal();
            const updatedState = await new Promise((resolve, reject) => {
                modal.addEventListener('close', (event) => {
                    if (event) {
                        reject(
                            new SolanaMobileWalletAdapterError(
                                SolanaMobileWalletAdapterErrorCode.ERROR_ASSOCIATION_CANCELLED,
                                'Wallet connection cancelled by user',
                                { event },
                            ),
                        );
                    }
                });
                lnaPermission.onchange = () => {
                    lnaPermission.onchange = null; // cleanup
                    resolve(lnaPermission.state);
                };
                modal.init();
                modal.open();
            });

            if (updatedState === 'granted') {
                // User has granted the permission, now we need another click to continue
                // Note: this is required to avoid being blocked by the browsers pop-up blocker
                const modal = new LocalConnectionModal();
                await new Promise((resolve, reject) => {
                    modal.addEventListener('close', (event) => {
                        if (event) {
                            reject(
                                new SolanaMobileWalletAdapterError(
                                    SolanaMobileWalletAdapterErrorCode.ERROR_ASSOCIATION_CANCELLED,
                                    'Wallet connection cancelled by user',
                                    { event },
                                ),
                            );
                        }
                    });
                    modal.initWithCallback(async () => {
                        resolve(true);
                    });
                    modal.open();
                });
                return;
            } else {
                // recurse, to avoid duplicating above logic
                return await checkLocalNetworkAccessPermission();
            }
        }

        // Shouldn't ever get here
        throw new SolanaMobileWalletAdapterError(
            SolanaMobileWalletAdapterErrorCode.ERROR_LOOPBACK_ACCESS_BLOCKED,
            'Local Network Access permission unknown',
        );
    } catch (e) {
        if (
            e instanceof TypeError &&
            (e.message.includes('loopback-network') || e.message.includes('local-network-access'))
        ) {
            // LNA permission API not found, continuing
            return;
        }

        // Re-throw existing adapter errors as-is
        if (e instanceof SolanaMobileWalletAdapterError) {
            throw e;
        }

        // An unknown error occurred, wrap it
        throw new SolanaMobileWalletAdapterError(
            SolanaMobileWalletAdapterErrorCode.ERROR_LOOPBACK_ACCESS_BLOCKED,
            e instanceof Error ? e.message : 'Local Network Access permission unknown',
        );
    }
}
