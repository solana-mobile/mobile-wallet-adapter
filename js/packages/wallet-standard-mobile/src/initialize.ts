import { registerWallet } from "@wallet-standard/wallet";
import { AddressSelector, AuthorizationResultCache, SolanaMobileWalletAdapterWallet } from "./wallet";
import { AppIdentity } from "@solana-mobile/mobile-wallet-adapter-protocol";
import { IdentifierString } from "@wallet-standard/base";
import getIsLocalAssociationSupported from "./getIsSupported";

export function registerMwa(config: {
    addressSelector: AddressSelector;
    appIdentity: AppIdentity;
    authorizationResultCache: AuthorizationResultCache;
    chain: IdentifierString;
    onWalletNotFound: (mobileWalletAdapter: SolanaMobileWalletAdapterWallet) => Promise<void>;
}) {
    if (getIsLocalAssociationSupported()) {
        registerWallet(new SolanaMobileWalletAdapterWallet(config))
    } else {
        // TODO: register remote wallet on desktop envs
    }
}