import { SolanaMobileWalletAdapterError, SolanaMobileWalletAdapterErrorCode } from '@solana-mobile/mobile-wallet-adapter-protocol';
import { SolanaMobileWalletAdapterWallet } from './wallet.js';
import ErrorModal from './embedded-modal/errorModal.js';

async function defaultWalletNotFoundHandler(mwaWallet: SolanaMobileWalletAdapterWallet) {
    if (typeof window !== 'undefined') {
        const userAgent = window.navigator.userAgent.toLowerCase();
        const errorDialog = new ErrorModal();
        if (userAgent.includes('wv')) { // Android WebView
            // MWA is not supported in this browser so we inform the user
            errorDialog.initWithError(
                new SolanaMobileWalletAdapterError(
                    SolanaMobileWalletAdapterErrorCode.ERROR_BROWSER_NOT_SUPPORTED, 
                    ''
                ));
        } else { // Browser, user does not have a wallet installed.
            errorDialog.initWithError(
                new SolanaMobileWalletAdapterError(
                    SolanaMobileWalletAdapterErrorCode.ERROR_WALLET_NOT_FOUND, 
                    ''
                )
            );
        }
        errorDialog.open();
    }
}

export default function createDefaultWalletNotFoundHandler(): (
    mobileWalletAdapter: SolanaMobileWalletAdapterWallet,
) => Promise<void> {
    return defaultWalletNotFoundHandler;
}
