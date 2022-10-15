import { SolanaMobileWalletAdapter } from './adapter';

async function defaultWalletNotFoundHandler(mobileWalletAdapter: SolanaMobileWalletAdapter) {
    if (typeof window !== 'undefined') {
        window.location.assign(mobileWalletAdapter.url);
    }
}

export default function createDefaultWalletNotFoundHandler(): (
    mobileWalletAdapter: SolanaMobileWalletAdapter,
) => Promise<void> {
    return defaultWalletNotFoundHandler;
}
