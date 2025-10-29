import { registerWallet } from "@wallet-standard/wallet";
import { 
    AuthorizationCache, 
    ChainSelector, 
    LocalSolanaMobileWalletAdapterWallet, 
    RemoteSolanaMobileWalletAdapterWallet, 
    SolanaMobileWalletAdapterWallet 
} from "./wallet";
import { AppIdentity } from "@solana-mobile/mobile-wallet-adapter-protocol";
import { IdentifierArray } from "@wallet-standard/base";
import { getIsLocalAssociationSupported, getIsRemoteAssociationSupported, isWebView } from "./getIsSupported";

export function registerMwa(config: {
    appIdentity: AppIdentity;
    authorizationCache: AuthorizationCache;
    chains: IdentifierArray;
    chainSelector: ChainSelector;
    remoteHostAuthority?: string;
    onWalletNotFound: (mobileWalletAdapter: SolanaMobileWalletAdapterWallet) => Promise<void>;
}) {
    if (typeof window === 'undefined') {
        console.warn(`MWA not registered: no window object`)
        return
    }
    if (!window.isSecureContext) {
        console.warn(`MWA not registered: secure context required (https)`)
        return
    }

    // Local association technically is possible in a webview, but we prevent registration
    // by default because it usually fails in the most common cases (e.g wallet browsers).
    if (getIsLocalAssociationSupported() && !isWebView(navigator.userAgent)) {
        registerWallet(new LocalSolanaMobileWalletAdapterWallet(config))
    } else if (getIsRemoteAssociationSupported() && config.remoteHostAuthority !== undefined) {
        registerWallet(new RemoteSolanaMobileWalletAdapterWallet({ ...config, remoteHostAuthority: config.remoteHostAuthority }))
    } else {
        // currently not supported (non-Android mobile device)
    }
}