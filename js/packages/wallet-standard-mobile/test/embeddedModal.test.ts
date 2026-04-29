// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';

const { mockQrCodeToCanvas } = vi.hoisted(() => ({
    mockQrCodeToCanvas: vi.fn(),
}));

const { mockGetIsPwaLaunchedAsApp } = vi.hoisted(() => ({
    mockGetIsPwaLaunchedAsApp: vi.fn(),
}));

vi.mock('qrcode', () => ({
    default: {
        toCanvas: mockQrCodeToCanvas,
    },
}));

vi.mock('../src/getIsSupported.js', async () => {
    const actual = await vi.importActual<typeof import('../src/getIsSupported.js')>('../src/getIsSupported.js');

    return {
        ...actual,
        getIsPwaLaunchedAsApp: mockGetIsPwaLaunchedAsApp,
    };
});

import ErrorModal from '../src/embedded-modal/errorModal.js';
import EmbeddedLoadingSpinner from '../src/embedded-modal/loadingSpinner.js';
import LocalConnectionModal from '../src/embedded-modal/localConnectionModal.js';
import LoopbackPermissionBlockedModal from '../src/embedded-modal/loopbackBlockedModal.js';
import LoopbackPermissionModal from '../src/embedded-modal/loopbackPermissionModal.js';
import EmbeddedModal from '../src/embedded-modal/modal.js';
import RemoteConnectionModal from '../src/embedded-modal/remoteConnectionModal.js';

afterEach(() => {
    document.body.replaceChildren();
    mockGetIsPwaLaunchedAsApp.mockReset();
    mockQrCodeToCanvas.mockReset();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
});

describe('EmbeddedModal', () => {
    it('injects modal content, opens, closes, and removes close listeners', async () => {
        const modal = new TestModal();
        const closeListener = vi.fn();

        const removeListener = modal.addEventListener('close', closeListener);

        await modal.init();

        const dom = getDom(modal);
        const root = getRoot(dom);

        expect(document.body.children).toHaveLength(1);
        expect(dom.getElementById('test-modal-content')).not.toBeNull();
        expect(dom.getElementById('mobile-wallet-adapter-embedded-modal-styles')?.textContent).toContain(
            '.test-modal-content',
        );
        expect(root.style.display).toBe('none');

        modal.open();

        expect(root.style.display).toBe('flex');

        const escapeEvent = new KeyboardEvent('keydown', { key: 'Escape' });

        document.dispatchEvent(escapeEvent);

        expect(root.style.display).toBe('none');
        expect(closeListener).toHaveBeenCalledWith(escapeEvent);

        removeListener();
        modal.close();

        expect(closeListener).toHaveBeenCalledTimes(1);
    });

    it('closes when the backdrop is clicked', async () => {
        const modal = new TestModal();
        const closeListener = vi.fn();

        modal.addEventListener('close', closeListener);
        await modal.init();
        modal.open();

        const backdrop = getDom(modal).querySelector('[data-modal-close]');

        backdrop?.dispatchEvent(new MouseEvent('click', { bubbles: true }));

        expect(getRoot(getDom(modal)).style.display).toBe('none');
        expect(closeListener).toHaveBeenCalledWith(expect.any(MouseEvent));
    });
});

describe('EmbeddedLoadingSpinner', () => {
    it('injects, opens, closes, and handles escape key closure', async () => {
        const spinner = new EmbeddedLoadingSpinner();
        const closeListener = vi.fn();

        spinner.addEventListener('close', closeListener);

        await spinner.init();
        spinner.open();

        const root = getRoot(getDom(spinner));

        expect(root.style.display).toBe('flex');

        const escapeEvent = new KeyboardEvent('keydown', { key: 'Escape' });

        document.dispatchEvent(escapeEvent);

        expect(root.style.display).toBe('none');
        expect(closeListener).toHaveBeenCalledWith(escapeEvent);
    });

    it('removes programmatic close listeners', async () => {
        const spinner = new EmbeddedLoadingSpinner();
        const closeListener = vi.fn();

        const removeListener = spinner.addEventListener('close', closeListener);

        await spinner.init();
        removeListener();
        spinner.close();

        expect(closeListener).not.toHaveBeenCalled();
    });
});

describe('concrete embedded modals', () => {
    it('renders wallet-not-found, browser-not-supported, and generic error messages', () => {
        const walletNotFoundModal = new ErrorModal();

        walletNotFoundModal.initWithError(createMwaError('ERROR_WALLET_NOT_FOUND'));

        expect(getDom(walletNotFoundModal).getElementById('mobile-wallet-adapter-error-message')?.textContent).toBe(
            'To use mobile wallet adapter, you must have a compatible mobile wallet application installed on your device.',
        );

        document.body.replaceChildren();

        const browserUnsupportedModal = new ErrorModal();

        browserUnsupportedModal.initWithError(createMwaError('ERROR_BROWSER_NOT_SUPPORTED'));

        expect(getDom(browserUnsupportedModal).getElementById('mobile-wallet-adapter-error-message')?.textContent).toBe(
            'This browser appears to be incompatible with mobile wallet adapter. Open this page in a compatible mobile browser app and try again.',
        );
        expect(
            getDom(browserUnsupportedModal).getElementById('mobile-wallet-adapter-error-action')?.style.display,
        ).toBe('none');

        document.body.replaceChildren();

        const genericErrorModal = new ErrorModal();

        genericErrorModal.initWithError(new Error('Unexpected'));

        expect(getDom(genericErrorModal).getElementById('mobile-wallet-adapter-error-message')?.textContent).toBe(
            'An unexpected error occurred: Unexpected',
        );
    });

    it('runs the local connection callback when the launch button is clicked', async () => {
        const callback = vi.fn().mockResolvedValue(undefined);
        const modal = new LocalConnectionModal();

        modal.initWithCallback(callback);
        modal.addEventListener('close', vi.fn());

        getDom(modal)
            .getElementById('mobile-wallet-adapter-launch-action')
            ?.dispatchEvent(new MouseEvent('click', { bubbles: true }));

        await Promise.resolve();

        expect(callback).toHaveBeenCalledTimes(1);
    });

    it('requests local network permission from the loopback permission modal launch action', async () => {
        const fetchMock = vi.fn().mockRejectedValue(new Error('ignored'));
        const closeListener = vi.fn();
        const modal = new LoopbackPermissionModal();

        vi.stubGlobal('fetch', fetchMock);

        modal.addEventListener('close', closeListener);
        await modal.init();

        getDom(modal)
            .getElementById('mobile-wallet-adapter-launch-action')
            ?.dispatchEvent(new MouseEvent('click', { bubbles: true }));

        await Promise.resolve();
        await Promise.resolve();

        expect(fetchMock).toHaveBeenCalledWith('http://localhost');
        expect(closeListener).toHaveBeenCalledTimes(1);
    });

    it('renders app and browser-specific blocked permission instructions', async () => {
        mockGetIsPwaLaunchedAsApp.mockReturnValueOnce(true);

        const appModal = new LoopbackPermissionBlockedModal();

        await appModal.init();

        expect(getDom(appModal).textContent).toContain(
            'Long press the app icon on your home screen to open site settings',
        );

        document.body.replaceChildren();
        mockGetIsPwaLaunchedAsApp.mockReturnValueOnce(false);

        const browserModal = new LoopbackPermissionBlockedModal();

        await browserModal.init();

        expect(getDom(browserModal).textContent).toContain(
            'Tap the lock or settings icon in the address bar to open site settings',
        );
    });

    it('populates and replaces the remote connection QR code', async () => {
        const firstCanvas = document.createElement('canvas');
        const secondCanvas = document.createElement('canvas');
        const modal = new RemoteConnectionModal();

        mockQrCodeToCanvas.mockResolvedValueOnce(firstCanvas).mockResolvedValueOnce(secondCanvas);

        await modal.initWithQR('solana-wallet:/first');

        const dom = getDom(modal);
        const qrContainer = dom.getElementById('mobile-wallet-adapter-embedded-modal-qr-code-container');

        expect(mockQrCodeToCanvas).toHaveBeenCalledWith('solana-wallet:/first', { margin: 0, width: 200 });
        expect(qrContainer?.firstElementChild).toBe(firstCanvas);
        expect(dom.getElementById('mobile-wallet-adapter-embedded-modal-qr-placeholder')).toBeNull();

        await modal.populateQRCode('solana-wallet:/second');

        expect(mockQrCodeToCanvas).toHaveBeenCalledWith('solana-wallet:/second', { margin: 0, width: 200 });
        expect(qrContainer?.firstElementChild).toBe(secondCanvas);
    });
});

class TestModal extends EmbeddedModal {
    protected contentStyles = '.test-modal-content { color: red; }';
    protected contentHtml = '<div id="test-modal-content" class="test-modal-content">Test modal</div>';
}

function createMwaError(code: 'ERROR_BROWSER_NOT_SUPPORTED' | 'ERROR_WALLET_NOT_FOUND') {
    return {
        code,
        message: '',
        name: 'SolanaMobileWalletAdapterError',
    } as Error;
}

function getDom(modal: unknown): ShadowRoot {
    const dom = (modal as { dom?: ShadowRoot | null }).dom;

    if (!dom) {
        throw new Error('Expected modal DOM to be initialized');
    }

    return dom;
}

function getRoot(dom: ShadowRoot): HTMLElement {
    const root = dom.getElementById('mobile-wallet-adapter-embedded-root-ui');

    if (!root) {
        throw new Error('Expected modal root to be initialized');
    }

    return root;
}
