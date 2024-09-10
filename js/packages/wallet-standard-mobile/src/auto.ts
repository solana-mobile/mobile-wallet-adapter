import { SOLANA_MAINNET_CHAIN } from "@solana/wallet-standard-chains";
import createDefaultAddressSelector from "./createDefaultAddressSelector";
import createDefaultAuthorizationResultCache from "./createDefaultAuthorizationResultCache";
import createDefaultWalletNotFoundHandler from "./createDefaultWalletNotFoundHandler";
import { registerMwa } from "./initialize";

function getUriForAppIdentity() {
    const location = globalThis.location;
    if (!location) return;
    return `${location.protocol}//${location.host}`;
}

registerMwa({
    addressSelector: createDefaultAddressSelector(),
    appIdentity: {
        uri: getUriForAppIdentity(),
    },
    authorizationResultCache: createDefaultAuthorizationResultCache(),
    chain: SOLANA_MAINNET_CHAIN,
    onWalletNotFound: createDefaultWalletNotFoundHandler(),
})