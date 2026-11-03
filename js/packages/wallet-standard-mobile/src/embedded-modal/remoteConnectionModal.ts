import QRCode from 'qrcode';

import EmbeddedModal from './modal.js';

export default class RemoteConnectionModal extends EmbeddedModal<'mobile-wallet-adapter-remote-connection-modal'> {
    protected readonly tag = 'mobile-wallet-adapter-remote-connection-modal';

    async initWithQR(qrCode: string) {
        this.init();
        await this.populateQRCode(qrCode);
    }

    async populateQRCode(qrUrl: string) {
        if (!this.element) {
            console.error('QRCode Container not found');
            return;
        }
        this.element.qrCode = await QRCode.toCanvas(qrUrl, { width: 200, margin: 0 });
    }
}
