import EmbeddedModal from "./modal";

import { 
    SolanaMobileWalletAdapterError, 
    SolanaMobileWalletAdapterErrorCode 
} from "@solana-mobile/mobile-wallet-adapter-protocol";

const WALLET_NOT_FOUND_ERROR_MESSAGE = 
    'To use Mobile Wallet Adapter, you must have a compatible mobile wallet application installed on your device. ' + 
    'For a list of compatible wallet apps, see <a href="https://wallets.solanamobile.com/">here.</a>';
    
const BROWSER_NOT_SUPPORTED_ERROR_MESSAGE = 
    'This browser appears to be incompatible with Mobile Wallet Adapter. Open this page in a compatible mobile browser app and try again.';

export default class ErrorModal extends EmbeddedModal {
    protected contentStyles = css;
    protected contentHtml = ErrorDialogHtml;

    initWithError(error: Error) {
        super.init();
        this.populateError(error);
    }

    private populateError(error: Error) {
        const errorMessageElement = this.dom?.getElementById('mobile-wallet-adapter-error-message');
        if(errorMessageElement) {
            if (error.name === 'SolanaMobileWalletAdapterError') {
                switch (
                    (error as SolanaMobileWalletAdapterError<
                        typeof SolanaMobileWalletAdapterErrorCode[keyof typeof SolanaMobileWalletAdapterErrorCode]
                    >).code
                ) {
                    case 'ERROR_WALLET_NOT_FOUND':
                        errorMessageElement.innerHTML = WALLET_NOT_FOUND_ERROR_MESSAGE;
                        return;
                    case 'ERROR_BROWSER_NOT_SUPPORTED':
                        errorMessageElement.innerHTML = BROWSER_NOT_SUPPORTED_ERROR_MESSAGE;
                        return;
                }
            }
            errorMessageElement.innerHTML = `An unexpected error occurred: ${error.message}`;
        } else {
            console.log('Failed to locate error dialog element');
        }
    }
}

const ErrorDialogHtml = `
<div class="mobile-wallet-adapter-embedded-modal-title">Mobile Wallet Adapter Failed to Connect</div>
<div id="mobile-wallet-adapter-error-message" class="mobile-wallet-adapter-embedded-modal-subtitle"></div>
<div class="mobile-wallet-adapter-embedded-modal-divider"><hr></div>
<div class="mobile-wallet-adapter-embedded-modal-footer">
    For more information about Mobile Wallet Adapter and the Solana Mobile Stack, join our <a href="https://discord.gg/solanamobile">discord.</a>
</div>
`;

const css = `
.mobile-wallet-adapter-embedded-modal-content {
    text-align: center;
}

.mobile-wallet-adapter-embedded-modal-title {
    margin: 20px 100px auto 100px;
    color: #000000;
    font-size: 2.5em;
    font-weight: 600;
}

.mobile-wallet-adapter-embedded-modal-subtitle {
    margin: 30px 60px auto 60px;
    color: #000000;
    font-size: 1.25em;
    font-weight: 500;
}

.mobile-wallet-adapter-embedded-modal-divider {
    margin-top: 20px;
    padding-left: 10px;
    padding-right: 10px;
}

.mobile-wallet-adapter-embedded-modal-divider hr {
    border-top: 1px solid #D9DEDE;
}

.mobile-wallet-adapter-embedded-modal-footer {
    margin: auto;
    margin-right: 60px;
    margin-left: 60px;
    padding: 20px;
    color: #6E8286;
}

/* Smaller screens */
@media all and (max-width: 600px) {
    .mobile-wallet-adapter-embedded-modal-title {
        font-size: 1.5em;
        margin-right: 20px;
        margin-left: 20px;
    }
    .mobile-wallet-adapter-embedded-modal-subtitle {
        margin-right: 30px;
        margin-left: 30px;
    }
    .mobile-wallet-adapter-embedded-modal-footer {
        margin-right: 20px;
        margin-left: 20px;
    }
}
`;