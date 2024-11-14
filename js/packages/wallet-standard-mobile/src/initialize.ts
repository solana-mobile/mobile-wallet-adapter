import { registerWallet } from "@wallet-standard/wallet";
import { 
    AddressSelector, 
    AuthorizationResultCache, 
    LocalSolanaMobileWalletAdapterWallet, 
    RemoteSolanaMobileWalletAdapterWallet, 
    SolanaMobileWalletAdapterWallet 
} from "./wallet";
import { AppIdentity } from "@solana-mobile/mobile-wallet-adapter-protocol";
import { IdentifierString } from "@wallet-standard/base";
import { getIsLocalAssociationSupported, getIsRemoteAssociationSupported } from "./getIsSupported";

export function registerMwa(config: {
    addressSelector: AddressSelector;
    appIdentity: AppIdentity;
    authorizationResultCache: AuthorizationResultCache;
    chain: IdentifierString;
    remoteHostAuthority?: string;
    onWalletNotFound: (mobileWalletAdapter: SolanaMobileWalletAdapterWallet) => Promise<void>;
}) {
    if (getIsLocalAssociationSupported()) {
        registerWallet(new LocalSolanaMobileWalletAdapterWallet(config))
    } else if (getIsRemoteAssociationSupported() && config.remoteHostAuthority !== undefined) {
        registerWallet(new RemoteSolanaMobileWalletAdapterWallet({ ...config, remoteHostAuthority: config.remoteHostAuthority }))
    } else {
        // currently not supported (non-Android mobile device)
    }
}