import EmbeddedModal from './modal.js';

export default class LocalConnectionModal extends EmbeddedModal<'mobile-wallet-adapter-local-connection-modal'> {
    protected readonly tag = 'mobile-wallet-adapter-local-connection-modal';

    initWithCallback(callback: () => Promise<void>) {
        this.init();
        const element = this.element!;
        element.onConnect = () => {
            element.onConnect = undefined;
            this.close();
            callback();
        };
    }
}
