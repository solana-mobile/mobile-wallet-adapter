import { SolanaMobileWalletAdapterWallet } from './wallet.js';

async function defaultWalletNotFoundHandler(mwaWallet: SolanaMobileWalletAdapterWallet) {
    if (typeof window !== 'undefined') {
        window.location.assign(mwaWallet.url);
    }
}

export default function createDefaultWalletNotFoundHandler(): (
    mobileWalletAdapter: SolanaMobileWalletAdapterWallet,
) => Promise<void> {
    return defaultWalletNotFoundHandler;
}
