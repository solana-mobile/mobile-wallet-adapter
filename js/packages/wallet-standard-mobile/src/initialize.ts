import { AppIdentity } from '@solana-mobile/mobile-wallet-adapter-protocol';
import { IdentifierArray } from '@wallet-standard/base';
import { registerWallet } from '@wallet-standard/wallet';

import {
    getIsLocalAssociationSupported,
    getIsRemoteAssociationSupported,
    isSolanaMobileWebShell,
    isWebView,
} from './getIsSupported.js';
import {
    AuthorizationCache,
    ChainSelector,
    LocalSolanaMobileWalletAdapterWallet,
    RemoteSolanaMobileWalletAdapterWallet,
    SolanaMobileWalletAdapterWallet,
} from './wallet.js';

export function registerMwa(
    config: {
        appIdentity: AppIdentity;
        authorizationCache: AuthorizationCache;
        chains: IdentifierArray;
        chainSelector: ChainSelector;
        onWalletNotFound: (mobileWalletAdapter: SolanaMobileWalletAdapterWallet) => Promise<void>;
    } & ({ remoteHostAuthority?: string } | { nostrRelay: string }),
) {
    if (typeof window === 'undefined') {
        console.warn(`MWA not registered: no window object`);
        return;
    }
    if (!window.isSecureContext) {
        console.warn(`MWA not registered: secure context required (https)`);
        return;
    }

    const userAgent = navigator.userAgent;

    const allowLocalAssociation =
        getIsLocalAssociationSupported() && (!isWebView(userAgent) || isSolanaMobileWebShell(userAgent));

    // Local association technically is possible in a webview, but we prevent registration
    // by default because it usually fails in the most common cases (e.g wallet browsers).
    if (allowLocalAssociation) {
        registerWallet(new LocalSolanaMobileWalletAdapterWallet(config));
    } else if (
        getIsRemoteAssociationSupported() &&
        ('nostrRelay' in config || config.remoteHostAuthority !== undefined)
    ) {
        if ('nostrRelay' in config) {
            registerWallet(new RemoteSolanaMobileWalletAdapterWallet({ ...config, nostrRelay: config.nostrRelay }));
        } else {
            registerWallet(
                new RemoteSolanaMobileWalletAdapterWallet({
                    ...config,
                    remoteHostAuthority: config.remoteHostAuthority!,
                }),
            );
        }
    } else {
        // currently not supported (non-Android mobile device)
        console.warn(`MWA not registered: device or environment not supported`);
    }
}
