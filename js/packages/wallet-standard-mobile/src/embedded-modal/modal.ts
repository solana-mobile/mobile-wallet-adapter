import QRCode from 'qrcode';

import { BluetoothHtml } from './bluetooth-html.js';
import { QRCodeHtml } from './qrcode-html.js';
import { css } from './styles.js';

export default class EmbeddedModal {
    private _title: string;
    private _root: HTMLElement | null = null;

    constructor(title: string) {
        this._title = title;

        // Bind methods to ensure `this` context is correct
        this.init = this.init.bind(this);
        this.injectQRCodeHTML = this.injectQRCodeHTML.bind(this);
        this.injectBluetoothHTML = this.injectBluetoothHTML.bind(this);
        this.open = this.open.bind(this);
        this.close = this.close.bind(this);
        this.connect = this.connect.bind(this);

        this._root = document.getElementById('mobile-wallet-adapter-embedded-root-ui');
    }

    async init(qrCode: string) {
        console.log('Injecting modal');
        this.injectStyles();
        this.injectQRCodeHTML(qrCode);
    }

    setConnectionStatus(status: 'not-connected' | 'connecting' | 'connected') {
        if (!this._root) return;

        const statuses = ['not-connected', 'connecting', 'connected'];
        statuses.forEach((s) => {
            const el = this._root!.querySelector(`#status-${s}`);
            if (el instanceof HTMLElement) {
                el.style.display = s === status ? 'flex' : 'none';
            }
        });
    }

    private injectStyles() {
        // Check if the styles have already been injected
        if (document.getElementById('mobile-wallet-adapter-styles')) {
            return;
        }

        const styleElement = document.createElement('style');
        styleElement.id = 'mobile-wallet-adapter-styles';
        styleElement.textContent = css;
        document.head.appendChild(styleElement);
    }

    private async populateQRCode(qrUrl: string) {
        const qrcodeContainer = document.getElementById('mobile-wallet-adapter-embedded-modal-qr-code-container');
        if (qrcodeContainer) {
            const qrCodeElement = await QRCode.toCanvas(qrUrl, { width: 400 });
            if (qrcodeContainer.firstElementChild !== null) {
                qrcodeContainer.replaceChild(qrCodeElement, qrcodeContainer.firstElementChild);
            } else qrcodeContainer.appendChild(qrCodeElement);
        } else {
            console.error('QRCode Container not found');
        }
    }

    private injectQRCodeHTML(qrCode: string) {
        // Check if the HTML has already been injected
        if (document.getElementById('mobile-wallet-adapter-embedded-root-ui')) {
            if (!this._root) this._root = document.getElementById('mobile-wallet-adapter-embedded-root-ui');
            this.populateQRCode(qrCode);
            return;
        }

        // Create a container for the modal
        this._root = document.createElement('div');
        this._root.id = 'mobile-wallet-adapter-embedded-root-ui';
        this._root.className = 'mobile-wallet-adapter-embedded-modal';
        this._root.innerHTML = QRCodeHtml;
        this._root.style.display = 'none';

        // Append the modal to the body
        document.body.appendChild(this._root);

        // Render the QRCode
        this.populateQRCode(qrCode);

        this.attachEventListeners();
    }

    private injectBluetoothHTML() {
        // Check if the HTML has already been injected
        if (document.getElementById('mobile-wallet-adapter-embedded-root-ui')) {
            return;
        }

        this._root = document.createElement('div');
        this._root.id = 'mobile-wallet-adapter-embedded-root-ui';
        this._root.className = 'mobile-wallet-adapter-embedded-modal';
        this._root.innerHTML = BluetoothHtml;
        document.body.appendChild(this._root);

        this.attachEventListeners();
    }

    private attachEventListeners() {
        if (!this._root) return;

        const closeBtn = this._root.querySelector('#mobile-wallet-adapter-embedded-modal-close');
        const cancelBtn = this._root.querySelector('#cancel-btn');
        const connectBtn = this._root.querySelector('#connect-btn');

        closeBtn?.addEventListener('click', () => this.close());
        cancelBtn?.addEventListener('click', () => this.close());
        connectBtn?.addEventListener('click', () => this.connect());
    }

    open() {
        console.debug('Modal open');
        if (this._root) {
            this._root.style.display = 'flex';
            this.setConnectionStatus('not-connected'); // Reset status when opening
        }
    }

    close() {
        console.debug('Modal close');
        if (this._root) {
            this._root.style.display = 'none';
            this.setConnectionStatus('not-connected'); // Reset status when closing
        }
    }

    private connect() {
        console.log('Connecting...');
        // Mock connection
        this.setConnectionStatus('connecting');

        // Simulate connection process
        setTimeout(() => {
            this.setConnectionStatus('connected');
            console.log('Connected!');
        }, 5000); // 5 seconds delay
    }
}
