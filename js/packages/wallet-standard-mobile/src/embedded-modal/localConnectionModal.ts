import { launchAssociation } from "@solana-mobile/mobile-wallet-adapter-protocol";
import EmbeddedModal from "./modal";

export default class LocalConnectionModal extends EmbeddedModal {
    protected contentStyles = css;
    protected contentHtml = ErrorDialogHtml;

    initWithAssociationUrl(associationUrl: URL) {
        super.init();
        this.#prepareLaunchAction(associationUrl);
    }

    #prepareLaunchAction(url: URL) {
        const launchButton = this.dom?.getElementById("mobile-wallet-adapter-launch-action");
        const listener = async (event?: any) => {
            await launchAssociation(url);
            launchButton?.removeEventListener('click', listener);
        }
        launchButton?.addEventListener('click', listener);
    }
}

const ErrorDialogHtml = `
<svg class="mobile-wallet-adapter-embedded-modal-launch-icon" xmlns="http://www.w3.org/2000/svg" height="50px" viewBox="0 -960 960 960" width="50px" fill="#000000">
    <path d="M200-120q-33 0-56.5-23.5T120-200v-560q0-33 23.5-56.5T200-840h280v80H200v560h560v-280h80v280q0 33-23.5 56.5T760-120H200Zm188-212-56-56 372-372H560v-80h280v280h-80v-144L388-332Z"/>
</svg>
<div class="mobile-wallet-adapter-embedded-modal-title">Open Wallet App</div>
<div id="mobile-wallet-adapter-local-launch-message" class="mobile-wallet-adapter-embedded-modal-subtitle">
    Press the button below to open your wallet app. If you have multiple compatible wallets installed, you will be asked to choose one. Other instruction or info for the user can go here.
</div>
<div>
    <button data-modal-action id="mobile-wallet-adapter-launch-action" class="mobile-wallet-adapter-embedded-modal-launch-action">
        Open Wallet
    </button>
</div>
`;

const css = `
.mobile-wallet-adapter-embedded-modal-content {
    text-align: center;
}

.mobile-wallet-adapter-embedded-modal-launch-icon {
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

.mobile-wallet-adapter-embedded-modal-launch-action {
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