import { 
    type SolanaMobileWalletAdapterError, 
    type SolanaMobileWalletAdapterErrorCode 
} from '@solana-mobile/mobile-wallet-adapter-protocol';
import { SolanaMobileWalletAdapterWallet } from './wallet.js';
import ErrorModal from './embedded-modal/errorModal.js';

export async function defaultErrorModalWalletNotFoundHandler() {
    if (typeof window !== 'undefined') {
        const userAgent = window.navigator.userAgent.toLowerCase();
        const errorDialog = new ErrorModal();
        if (userAgent.includes('wv')) { // Android WebView
            // MWA is not supported in this browser so we inform the user
            // errorDialog.initWithError(
            //     new SolanaMobileWalletAdapterError(
            //         SolanaMobileWalletAdapterErrorCode.ERROR_BROWSER_NOT_SUPPORTED, 
            //         ''
            //     )
            // );
            // TODO: investigate why instantiating a new SolanaMobileWalletAdapterError here breaks treeshaking 
            errorDialog.initWithError({ 
                name: 'SolanaMobileWalletAdapterError', 
                code: 'ERROR_BROWSER_NOT_SUPPORTED', 
                message: '' 
            } as SolanaMobileWalletAdapterError<typeof SolanaMobileWalletAdapterErrorCode[keyof typeof SolanaMobileWalletAdapterErrorCode]>);
        } else { // Browser, user does not have a wallet installed.
            // errorDialog.initWithError(
            //     new SolanaMobileWalletAdapterError(
            //         SolanaMobileWalletAdapterErrorCode.ERROR_WALLET_NOT_FOUND, 
            //         ''
            //     )
            // );
            // TODO: investigate why instantiating a new SolanaMobileWalletAdapterError here breaks treeshaking 
            errorDialog.initWithError({ 
                name: 'SolanaMobileWalletAdapterError', 
                code: 'ERROR_WALLET_NOT_FOUND', 
                message: '' 
            } as SolanaMobileWalletAdapterError<typeof SolanaMobileWalletAdapterErrorCode[keyof typeof SolanaMobileWalletAdapterErrorCode]>);
        }
        errorDialog.open();
    }
}

export default function createDefaultWalletNotFoundHandler(): (
    mobileWalletAdapter: SolanaMobileWalletAdapterWallet,
) => Promise<void> {
    return async () => { defaultErrorModalWalletNotFoundHandler(); };
}
