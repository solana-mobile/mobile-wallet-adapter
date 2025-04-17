import { SOLANA_MAINNET_CHAIN } from "@solana/wallet-standard-chains";
import createDefaultAuthorizationCache from "./createDefaultAuthorizationCache";
import createDefaultChainSelector from "./createDefaultChainSelector";
import createDefaultWalletNotFoundHandler from "./createDefaultWalletNotFoundHandler";
import { registerMwa } from "./initialize";

function getUriForAppIdentity() {
    const location = globalThis.location;
    if (!location) return;
    return `${location.protocol}//${location.host}`;
}

registerMwa({
    appIdentity: {
        uri: getUriForAppIdentity(),
    },
    authorizationCache: createDefaultAuthorizationCache(),
    chains: [SOLANA_MAINNET_CHAIN],
    chainSelector: createDefaultChainSelector(),
    onWalletNotFound: createDefaultWalletNotFoundHandler(),
})