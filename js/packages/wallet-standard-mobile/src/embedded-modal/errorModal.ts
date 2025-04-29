import { 
    type SolanaMobileWalletAdapterError, 
    type SolanaMobileWalletAdapterErrorCode 
} from "@solana-mobile/mobile-wallet-adapter-protocol";
import EmbeddedModal from "./modal";

const WALLET_NOT_FOUND_ERROR_MESSAGE = 
    'To use mobile wallet adapter, you must have a compatible mobile wallet application installed on your device.';
    
const BROWSER_NOT_SUPPORTED_ERROR_MESSAGE = 
    'This browser appears to be incompatible with mobile wallet adapter. Open this page in a compatible mobile browser app and try again.';

export default class ErrorModal extends EmbeddedModal {
    protected contentStyles = css;
    protected contentHtml = ErrorDialogHtml;

    initWithError(error: Error) {
        super.init();
        this.populateError(error);
    }

    private populateError(error: Error) {
        const errorMessageElement = this.dom?.getElementById('mobile-wallet-adapter-error-message');
        const actionBtn = this.dom?.getElementById('mobile-wallet-adapter-error-action');
        if(errorMessageElement) {
            if (error.name === 'SolanaMobileWalletAdapterError') {
                switch (
                    (error as SolanaMobileWalletAdapterError<
                        typeof SolanaMobileWalletAdapterErrorCode[keyof typeof SolanaMobileWalletAdapterErrorCode]
                    >).code
                ) {
                    case 'ERROR_WALLET_NOT_FOUND':
                        errorMessageElement.innerHTML = WALLET_NOT_FOUND_ERROR_MESSAGE;
                        if (actionBtn) actionBtn.addEventListener('click', () => {
                            window.location.href = 'https://solanamobile.com/wallets';
                        });
                        return;
                    case 'ERROR_BROWSER_NOT_SUPPORTED':
                        errorMessageElement.innerHTML = BROWSER_NOT_SUPPORTED_ERROR_MESSAGE;
                        if (actionBtn) actionBtn.style.display = 'none'
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
<svg class="mobile-wallet-adapter-embedded-modal-error-icon" xmlns="http://www.w3.org/2000/svg" height="50px" viewBox="0 -960 960 960" width="50px" fill="#000000"><path d="M 280,-80 Q 197,-80 138.5,-138.5 80,-197 80,-280 80,-363 138.5,-421.5 197,-480 280,-480 q 83,0 141.5,58.5 58.5,58.5 58.5,141.5 0,83 -58.5,141.5 Q 363,-80 280,-80 Z M 824,-120 568,-376 Q 556,-389 542.5,-402.5 529,-416 516,-428 q 38,-24 61,-64 23,-40 23,-88 0,-75 -52.5,-127.5 Q 495,-760 420,-760 345,-760 292.5,-707.5 240,-655 240,-580 q 0,6 0.5,11.5 0.5,5.5 1.5,11.5 -18,2 -39.5,8 -21.5,6 -38.5,14 -2,-11 -3,-22 -1,-11 -1,-23 0,-109 75.5,-184.5 Q 311,-840 420,-840 q 109,0 184.5,75.5 75.5,75.5 75.5,184.5 0,43 -13.5,81.5 Q 653,-460 629,-428 l 251,252 z m -615,-61 71,-71 70,71 29,-28 -71,-71 71,-71 -28,-28 -71,71 -71,-71 -28,28 71,71 -71,71 z"/></svg>
<div class="mobile-wallet-adapter-embedded-modal-title">We can't find a wallet.</div>
<div id="mobile-wallet-adapter-error-message" class="mobile-wallet-adapter-embedded-modal-subtitle"></div>
<div>
    <button data-error-action id="mobile-wallet-adapter-error-action" class="mobile-wallet-adapter-embedded-modal-error-action">
        Find a wallet
    </button>
</div>
`;

const css = `
.mobile-wallet-adapter-embedded-modal-content {
    text-align: center;
}

.mobile-wallet-adapter-embedded-modal-error-icon {
    margin-top: 24px;
}

.mobile-wallet-adapter-embedded-modal-title {
    margin: 18px 100px auto 100px;
    color: #000000;
    font-size: 2.75em;
    font-weight: 600;
}

.mobile-wallet-adapter-embedded-modal-subtitle {
    margin: 30px 60px 40px 60px;
    color: #000000;
    font-size: 1.25em;
    font-weight: 400;
}

.mobile-wallet-adapter-embedded-modal-error-action {
    display: block;
    width: 100%;
    height: 56px;
    /*margin-top: 40px;*/
    font-size: 1.25em;
    /*line-height: 24px;*/
    /*letter-spacing: -1%;*/
    background: #000000;
    color: #FFFFFF;
    border-radius: 18px;
}

/* Smaller screens */
@media all and (max-width: 600px) {
    .mobile-wallet-adapter-embedded-modal-title {
        font-size: 1.5em;
        margin-right: 12px;
        margin-left: 12px;
    }
    .mobile-wallet-adapter-embedded-modal-subtitle {
        margin-right: 12px;
        margin-left: 12px;
    }
}
`;