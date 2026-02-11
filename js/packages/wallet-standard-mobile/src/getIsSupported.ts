import { SolanaMobileWalletAdapterError, SolanaMobileWalletAdapterErrorCode } from "@solana-mobile/mobile-wallet-adapter-protocol";
import LocalConnectionModal from "./embedded-modal/localConnectionModal";
import LoopbackPermissionBlockedModal from "./embedded-modal/loopbackBlockedModal";
import LoopbackPermissionModal from "./embedded-modal/loopbackPermissionModal";

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
        userAgentString
    );
}

// Source: https://web.dev/learn/pwa/detection/
export function getIsPwaLaunchedAsApp() {
    // Check for Android TWA
    const isAndroidTwa = typeof document !== 'undefined' && document.referrer.startsWith('android-app://')

    // Check for display-mode: standalone, fullscreen, or minimal-ui
    if (typeof window == 'undefined' ) return isAndroidTwa;
    const isStandalone = window.matchMedia('(display-mode: standalone)').matches;
    const isFullscreen = window.matchMedia('(display-mode: fullscreen)').matches;
    const isMinimalUI = window.matchMedia('(display-mode: minimal-ui)').matches;
    
    // App mode if any of these conditions are true
    return isAndroidTwa || isStandalone || isFullscreen || isMinimalUI;
}

export async function checkLocalNetworkAccessPermission<TReturn>(onGranted: () => Promise<TReturn>): Promise<TReturn> {
    try {
        let lnaPermission: PermissionStatus = 
            await navigator.permissions.query({ name: "loopback-network" as PermissionName});
        if (lnaPermission.state === "granted") {
            console.log(`LNA permission already granted, continuing`);
            return await onGranted();
        } else if (lnaPermission.state === "denied") {
            console.log(`LNA permission denied, aborting`);
            const modal = new LoopbackPermissionBlockedModal();
            modal.init();
            modal.open();
            throw new SolanaMobileWalletAdapterError(
                SolanaMobileWalletAdapterErrorCode.ERROR_LOOPBACK_ACCESS_BLOCKED,
                'Local Network Access permission denied'
            );
        } else if (lnaPermission.state === "prompt") {
            console.log(`LNA permission is prompt, requesting...`);

            // Show permission explainer to user
            const modal = new LoopbackPermissionModal();
            modal.init();
            modal.open();

            // wait for the permission to change
            const updatedState = await new Promise(resolve => {
                lnaPermission.onchange = () => {
                    console.log(`LNA permission changed to: ${lnaPermission.state}`);
                    lnaPermission.onchange = null; // cleanup
                    resolve(lnaPermission.state);
                };
            });

            if (updatedState === "granted") {
                console.log(`LNA permission granted, continuing`);
                // User has granted the permission, now we need another click to continue
                // Note: this is required to avoid being blocked by the browsers pop-up blocker
                const modal = new LocalConnectionModal();
                await new Promise(resolve => {
                    modal.initWithCallback(async () => {
                        resolve(true);
                    });
                    modal.open();
                });
                return await onGranted();
            } else {
                // recurse, to avoid duplicating above logic
                return await checkLocalNetworkAccessPermission(onGranted);
            }
        }

        // Shouldn't ever get here
        console.log(`LNA permission state is unknown (${lnaPermission.state}), aborting`);
        throw new SolanaMobileWalletAdapterError(
            SolanaMobileWalletAdapterErrorCode.ERROR_LOOPBACK_ACCESS_BLOCKED,
            'Local Network Access permission unknown'
        );
    } catch (e) {  
        if (e instanceof TypeError && 
            (
                e.message.includes('loopback-network') || 
                e.message.includes('local-network-access')
            )
        ) {
            console.log(`LNA permission API not found, continuing`);
            return await onGranted();
        } 

        throw new SolanaMobileWalletAdapterError(
            SolanaMobileWalletAdapterErrorCode.ERROR_LOOPBACK_ACCESS_BLOCKED,
            e instanceof Error ? e.message : 'Local Network Access permission unknown'
        );
    }
}