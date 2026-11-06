import EmbeddedModal from './modal.js';

export default class LoopbackPermissionModal extends EmbeddedModal<'mobile-wallet-adapter-loopback-permission-modal'> {
    protected readonly tag = 'mobile-wallet-adapter-loopback-permission-modal';

    init() {
        super.init();
        const element = this.element!;
        element.onContinue = async () => {
            element.onContinue = undefined;
            try {
                // Trigger LNA permission prompting
                await fetch('http://localhost');
            } catch {
                /* Ignore errors from fetch */
            }
            this.close();
        };
    }
}
