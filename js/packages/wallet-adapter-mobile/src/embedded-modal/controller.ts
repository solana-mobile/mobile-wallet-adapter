import EmbeddedModal from './modal';

export default class EmbeddedModalController {
    private _modal: EmbeddedModal;
    private _connectionStatus: 'not-connected' | 'connecting' | 'connected' = 'not-connected';

    constructor(title: string) {
        this._modal = new EmbeddedModal(title);
    }

    async init() {
        await this._modal.init();
        this.attachEventListeners();
    }

    private attachEventListeners() {
        const connectBtn = document.querySelector('#connect-btn');
        connectBtn?.addEventListener('click', () => this.connect());
    }

    open() {
        this._modal.open();
        this.setConnectionStatus('not-connected');
    }

    close() {
        this._modal.close();
        this.setConnectionStatus('not-connected');
    }

    private setConnectionStatus(status: 'not-connected' | 'connecting' | 'connected') {
        this._connectionStatus = status;
        this._modal.setConnectionStatus(status);
    }

    private async connect() {
        console.log('Connecting...');
        this.setConnectionStatus('connecting');

        try {
            // Simulating connection process
            await new Promise((resolve) => setTimeout(resolve, 5000));
            this.setConnectionStatus('connected');
            console.log('Connected!');
        } catch (error) {
            console.error('Connection failed:', error);
            this.setConnectionStatus('not-connected');
        }
    }
}
