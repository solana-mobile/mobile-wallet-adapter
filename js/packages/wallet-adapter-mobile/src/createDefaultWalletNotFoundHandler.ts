import { SolanaMobileWalletAdapter } from './adapter.js';
import { defaultErrorModalWalletNotFoundHandler } from '@solana-mobile/wallet-standard-mobile';

async function defaultWalletNotFoundHandler(mobileWalletAdapter: SolanaMobileWalletAdapter) {
    return defaultErrorModalWalletNotFoundHandler();
}

export default function createDefaultWalletNotFoundHandler(): (
    mobileWalletAdapter: SolanaMobileWalletAdapter,
) => Promise<void> {
    return defaultWalletNotFoundHandler;
}
