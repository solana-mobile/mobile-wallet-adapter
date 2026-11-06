import EmbeddedModal from './modal.js';

export default class ErrorModal extends EmbeddedModal<'mobile-wallet-adapter-error-modal'> {
    protected readonly tag = 'mobile-wallet-adapter-error-modal';

    initWithError(error: Error) {
        this.init();
        this.element!.error = error;
    }
}
