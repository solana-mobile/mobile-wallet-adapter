import { defaultErrorModalWalletNotFoundHandler } from '@solana-mobile/wallet-standard-mobile';

import { SolanaMobileWalletAdapter } from './adapter.js';

async function defaultWalletNotFoundHandler(_mobileWalletAdapter: SolanaMobileWalletAdapter) {
    return defaultErrorModalWalletNotFoundHandler();
}

export default function createDefaultWalletNotFoundHandler(): (
    mobileWalletAdapter: SolanaMobileWalletAdapter,
) => Promise<void> {
    return defaultWalletNotFoundHandler;
}
