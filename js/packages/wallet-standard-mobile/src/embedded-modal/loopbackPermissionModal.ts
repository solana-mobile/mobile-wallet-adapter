import EmbeddedModal from './modal.js';

export default class LoopbackPermissionModal extends EmbeddedModal<'mobile-wallet-adapter-loopback-permission-modal'> {
    protected readonly tag = 'mobile-wallet-adapter-loopback-permission-modal';

    init() {
        super.init();
        const element = this.element!;
        // Stays open, and can prompt again, until the permission state changes; the permission flow closes it.
        element.onContinue = async () => {
            try {
                // Trigger LNA permission prompting
                await fetch('http://localhost');
            } catch {
                /* Ignore errors from fetch */
            }
        };
    }
}
