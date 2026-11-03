import { getIsPwaLaunchedAsApp } from '../getIsSupported.js';
import EmbeddedModal from './modal.js';

export default class LoopbackPermissionBlockedModal extends EmbeddedModal<'mobile-wallet-adapter-loopback-blocked-modal'> {
    protected readonly tag = 'mobile-wallet-adapter-loopback-blocked-modal';

    init() {
        super.init();
        this.element!.instruction = getIsPwaLaunchedAsApp()
            ? 'Long press the app icon on your home screen to open site settings'
            : 'Tap the lock or settings icon in the address bar to open site settings';
    }
}
