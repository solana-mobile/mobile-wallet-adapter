import { css, type CSSResultGroup, html } from 'lit';

import { EmbeddedModalElement } from './modalElement.js';

export class RemoteConnectionModalElement extends EmbeddedModalElement {
    static properties = {
        qrCode: { attribute: false },
    };

    static get styles(): CSSResultGroup {
        return [
            EmbeddedModalElement.styles,
            css`
                .qr-content {
                    display: flex;
                    margin-top: 10px;
                    padding: 10px;
                }

                .qr-content > div:first-child {
                    display: flex;
                    flex-direction: column;
                    flex: 2;
                    margin-top: auto;
                    margin-right: 30px;
                }

                .qr-content > div:nth-child(2) {
                    display: flex;
                    flex-direction: column;
                    flex: 1;
                    margin-left: auto;
                }

                .footer {
                    display: flex;
                    padding: 10px;
                }

                .title {
                    color: #000000;
                    font-size: 2.5em;
                    font-weight: 600;
                }

                .qr-label {
                    text-align: right;
                    color: #000000;
                }

                .qr-code-container {
                    margin-left: auto;
                }

                .qr-placeholder {
                    margin-left: auto;
                    min-width: 200px;
                    min-height: 200px;
                    background: linear-gradient(-60deg, #f7f8f8 30%, #eceeee 50%, #f7f8f8 70%);
                    background-size: 200%;
                    animation: placeholderAnimate 2.7s linear infinite;
                    border-radius: 12px;
                }

                .divider {
                    margin-top: 20px;
                    padding-left: 10px;
                    padding-right: 10px;
                }

                .divider hr {
                    border-top: 1px solid #d9dede;
                }

                .subtitle {
                    margin: auto;
                    margin-right: 60px;
                    padding: 20px;
                    color: #6e8286;
                }

                .progress-badge {
                    display: flex;
                    background: #f7f8f8;
                    height: 56px;
                    min-width: 200px;
                    margin: auto;
                    padding-left: 20px;
                    padding-right: 20px;
                    border-radius: 18px;
                    color: #a8b6b8;
                    align-items: center;
                }

                .progress-badge > div:first-child {
                    margin-left: auto;
                    margin-right: 20px;
                }

                .progress-badge > div:nth-child(2) {
                    margin-right: auto;
                }

                /* Smaller screens */
                @media all and (max-width: 600px) {
                    .card {
                        text-align: center;
                    }
                    .qr-content {
                        flex-direction: column;
                    }
                    .qr-content > div:first-child {
                        margin: auto;
                    }
                    .qr-content > div:nth-child(2) {
                        margin: auto;
                        flex: 2 auto;
                    }
                    .footer {
                        flex-direction: column;
                    }
                    .icon {
                        display: none;
                    }
                    .title {
                        font-size: 1.5em;
                    }
                    .subtitle {
                        margin-right: unset;
                    }
                    .qr-label {
                        text-align: center;
                    }
                    .qr-code-container {
                        margin: auto;
                    }
                    .qr-placeholder {
                        margin: auto;
                    }
                }

                /* QR Placeholder */
                @keyframes placeholderAnimate {
                    0% {
                        background-position: 200% 0;
                    }
                    100% {
                        background-position: -200% 0;
                    }
                }

                /* Spinner */
                @keyframes spinLeft {
                    0% {
                        transform: rotate(20deg);
                    }
                    50% {
                        transform: rotate(160deg);
                    }
                    100% {
                        transform: rotate(20deg);
                    }
                }
                @keyframes spinRight {
                    0% {
                        transform: rotate(160deg);
                    }
                    50% {
                        transform: rotate(20deg);
                    }
                    100% {
                        transform: rotate(160deg);
                    }
                }
                @keyframes spin {
                    0% {
                        transform: rotate(0deg);
                    }
                    100% {
                        transform: rotate(2520deg);
                    }
                }

                .spinner {
                    position: relative;
                    width: 1.5em;
                    height: 1.5em;
                    margin: auto;
                    animation: spin 10s linear infinite;
                }
                .spinner::before {
                    content: '';
                    position: absolute;
                    top: 0;
                    bottom: 0;
                    left: 0;
                    right: 0;
                }
                .right,
                .rightWrapper,
                .left,
                .leftWrapper {
                    position: absolute;
                    top: 0;
                    overflow: hidden;
                    width: 0.75em;
                    height: 1.5em;
                }
                .left,
                .leftWrapper {
                    left: 0;
                }
                .right {
                    left: -12px;
                }
                .rightWrapper {
                    right: 0;
                }
                .circle {
                    border: 0.125em solid #a8b6b8;
                    width: 1.25em; /* 1.5em - 2*0.125em border */
                    height: 1.25em; /* 1.5em - 2*0.125em border */
                    border-radius: 0.75em; /* 0.5*1.5em spinner size 8 */
                }
                .left {
                    transform-origin: 100% 50%;
                    animation: spinLeft 2.5s cubic-bezier(0.2, 0, 0.8, 1) infinite;
                }
                .right {
                    transform-origin: 100% 50%;
                    animation: spinRight 2.5s cubic-bezier(0.2, 0, 0.8, 1) infinite;
                }
            `,
        ];
    }

    /** The rendered association QR code. A loading placeholder shows until it is set. */
    declare qrCode: HTMLCanvasElement | undefined;

    protected renderContent() {
        return html`
            <div class="qr-content">
                <div>
                    <svg class="icon" width="100%" height="100%">
                        <circle r="52" cx="53" cy="53" fill="#99b3be" stroke="#000000" stroke-width="2" />
                        <path
                            d="m 53,82.7305 c -3.3116,0 -6.1361,-1.169 -8.4735,-3.507 -2.338,-2.338 -3.507,-5.1625 -3.507,-8.4735 0,-3.3116 1.169,-6.1364 3.507,-8.4744 2.3374,-2.338 5.1619,-3.507 8.4735,-3.507 3.3116,0 6.1361,1.169 8.4735,3.507 2.338,2.338 3.507,5.1628 3.507,8.4744 0,3.311 -1.169,6.1355 -3.507,8.4735 -2.3374,2.338 -5.1619,3.507 -8.4735,3.507 z m 0.007,-5.25 c 1.8532,0 3.437,-0.6598 4.7512,-1.9793 1.3149,-1.3195 1.9723,-2.9058 1.9723,-4.7591 0,-1.8526 -0.6598,-3.4364 -1.9793,-4.7512 -1.3195,-1.3149 -2.9055,-1.9723 -4.7582,-1.9723 -1.8533,0 -3.437,0.6598 -4.7513,1.9793 -1.3148,1.3195 -1.9722,2.9058 -1.9722,4.7591 0,1.8527 0.6597,3.4364 1.9792,4.7512 1.3195,1.3149 2.9056,1.9723 4.7583,1.9723 z m -28,-33.5729 -3.85,-3.6347 c 4.1195,-4.025 8.8792,-7.1984 14.2791,-9.52 5.4005,-2.3223 11.2551,-3.4834 17.5639,-3.4834 6.3087,0 12.1634,1.1611 17.5639,3.4834 5.3999,2.3216 10.1596,5.495 14.2791,9.52 l -3.85,3.6347 C 77.2999,40.358 73.0684,37.5726 68.2985,35.5514 63.5292,33.5301 58.4296,32.5195 53,32.5195 c -5.4297,0 -10.5292,1.0106 -15.2985,3.0319 -4.7699,2.0212 -9.0014,4.8066 -12.6945,8.3562 z m 44.625,10.8771 c -2.2709,-2.1046 -4.7962,-3.7167 -7.5758,-4.8361 -2.7795,-1.12 -5.7983,-1.68 -9.0562,-1.68 -3.2579,0 -6.2621,0.56 -9.0125,1.68 -2.7504,1.1194 -5.2903,2.7315 -7.6195,4.8361 L 32.5189,51.15 c 2.8355,-2.6028 5.9777,-4.6086 9.4263,-6.0174 3.4481,-1.4087 7.133,-2.1131 11.0548,-2.1131 3.9217,0 7.5979,0.7044 11.0285,2.1131 3.43,1.4088 6.5631,3.4146 9.3992,6.0174 z"
                        />
                    </svg>
                    <div id="title" class="title">Remote Mobile Wallet Adapter</div>
                </div>
                <div>
                    <div>
                        <h4 class="qr-label">Open your wallet and scan this code</h4>
                    </div>
                    <div class="qr-code-container">${this.qrCode ?? html`<div class="qr-placeholder"></div>`}</div>
                </div>
            </div>
            <div class="divider"><hr /></div>
            <div class="footer">
                <div class="subtitle">
                    Follow the instructions on your device. When you're finished, this screen will update.
                </div>
                <div class="progress-badge">
                    <div>
                        <div class="spinner">
                            <div class="leftWrapper">
                                <div class="left">
                                    <div class="circle"></div>
                                </div>
                            </div>
                            <div class="rightWrapper">
                                <div class="right">
                                    <div class="circle"></div>
                                </div>
                            </div>
                        </div>
                    </div>
                    <div>Waiting for scan</div>
                </div>
            </div>
        `;
    }
}
