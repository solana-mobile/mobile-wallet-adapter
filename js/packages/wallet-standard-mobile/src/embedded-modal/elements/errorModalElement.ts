import { css, type CSSResultGroup, html, nothing } from 'lit';

import { EmbeddedModalElement } from './modalElement.js';

const WALLET_NOT_FOUND_ERROR_MESSAGE =
    'To use mobile wallet adapter, you must have a compatible mobile wallet application installed on your device.';

const BROWSER_NOT_SUPPORTED_ERROR_MESSAGE =
    'This browser appears to be incompatible with mobile wallet adapter. Open this page in a compatible mobile browser app and try again.';

const FIND_A_WALLET_URL = 'https://solanamobile.com/wallets';

export class ErrorModalElement extends EmbeddedModalElement {
    static properties = {
        error: { attribute: false },
    };

    static get styles(): CSSResultGroup {
        return [
            EmbeddedModalElement.styles,
            css`
                .content {
                    text-align: center;
                }
                .icon {
                    margin-top: 24px;
                }
                .title {
                    margin: 18px 100px auto 100px;
                    color: #000000;
                    font-size: 2.75em;
                    font-weight: 600;
                }
                .subtitle {
                    margin: 30px 60px 40px 60px;
                    color: #000000;
                    font-size: 1.25em;
                    font-weight: 400;
                }
                @media all and (max-width: 600px) {
                    .title {
                        font-size: 1.5em;
                        margin-right: 12px;
                        margin-left: 12px;
                    }
                    .subtitle {
                        margin-right: 12px;
                        margin-left: 12px;
                    }
                }
            `,
        ];
    }

    declare error: Error | undefined;

    #findWallet() {
        window.location.href = FIND_A_WALLET_URL;
    }

    protected renderContent() {
        const code =
            this.error?.name === 'SolanaMobileWalletAdapterError' && (this.error as Error & { code?: string }).code;
        const message =
            code === 'ERROR_WALLET_NOT_FOUND'
                ? WALLET_NOT_FOUND_ERROR_MESSAGE
                : code === 'ERROR_BROWSER_NOT_SUPPORTED'
                  ? BROWSER_NOT_SUPPORTED_ERROR_MESSAGE
                  : `An unexpected error occurred: ${this.error?.message}`;
        return html`
            <svg
                class="icon"
                xmlns="http://www.w3.org/2000/svg"
                height="50px"
                viewBox="0 -960 960 960"
                width="50px"
                fill="#000000"
            >
                <path
                    d="M 280,-80 Q 197,-80 138.5,-138.5 80,-197 80,-280 80,-363 138.5,-421.5 197,-480 280,-480 q 83,0 141.5,58.5 58.5,58.5 58.5,141.5 0,83 -58.5,141.5 Q 363,-80 280,-80 Z M 824,-120 568,-376 Q 556,-389 542.5,-402.5 529,-416 516,-428 q 38,-24 61,-64 23,-40 23,-88 0,-75 -52.5,-127.5 Q 495,-760 420,-760 345,-760 292.5,-707.5 240,-655 240,-580 q 0,6 0.5,11.5 0.5,5.5 1.5,11.5 -18,2 -39.5,8 -21.5,6 -38.5,14 -2,-11 -3,-22 -1,-11 -1,-23 0,-109 75.5,-184.5 Q 311,-840 420,-840 q 109,0 184.5,75.5 75.5,75.5 75.5,184.5 0,43 -13.5,81.5 Q 653,-460 629,-428 l 251,252 z m -615,-61 71,-71 70,71 29,-28 -71,-71 71,-71 -28,-28 -71,71 -71,-71 -28,28 71,71 -71,71 z"
                />
            </svg>
            <div id="title" class="title">We can't find a wallet.</div>
            <div class="subtitle">${message}</div>
            <div>
                ${
                    code === 'ERROR_WALLET_NOT_FOUND'
                        ? html`<button class="action" @click=${this.#findWallet}>Find a wallet</button>`
                        : nothing
                }
            </div>
        `;
    }
}
