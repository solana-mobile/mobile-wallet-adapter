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
import { getIsLocalAssociationSupported, getIsRemoteAssociationSupported } from "./getIsSupported";

export function registerMwa(config: RemoteSolanaMobileWalletAdapterWalletOptions) {
    if (getIsLocalAssociationSupported()) {
        registerWallet(new LocalSolanaMobileWalletAdapterWallet(config))
    } else if (getIsRemoteAssociationSupported() && config.remoteHostAuthority !== undefined) {
        registerWallet(new RemoteSolanaMobileWalletAdapterWallet({ ...config, remoteHostAuthority: config.remoteHostAuthority }))
    } else {
        // currently not supported (non-Android mobile device)
    }
}