import { defaultErrorModalWalletNotFoundHandler } from '@solana-mobile/wallet-standard-mobile';

import { SolanaMobileWalletAdapter } from './adapter.js';

async function defaultWalletNotFoundHandler(_mobileWalletAdapter: SolanaMobileWalletAdapter) {
    return defaultErrorModalWalletNotFoundHandler();
}

export function createDefaultWalletNotFoundHandler(): (
    mobileWalletAdapter: SolanaMobileWalletAdapter,
) => Promise<void> {
    return defaultWalletNotFoundHandler;
}

/**
 * @deprecated Use {@link createDefaultWalletNotFoundHandler} instead.
 */
export default createDefaultWalletNotFoundHandler;
