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
import type EmbeddedModal from '../src/embedded-modal/modal.js';
import RemoteConnectionModal from '../src/embedded-modal/remoteConnectionModal.js';

afterEach(() => {
    document.body.replaceChildren();
    mockGetIsPwaLaunchedAsApp.mockReset();
    mockQrCodeToCanvas.mockReset();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
});

describe('EmbeddedModal', () => {
    it('no-ops open and close before initialization', () => {
        const modal = new ErrorModal();

        expect(() => modal.open()).not.toThrow();
        expect(() => modal.close()).not.toThrow();
        expect(document.body.children).toHaveLength(0);
    });

    it('adds the element on open and removes it on close', async () => {
        const modal = new ErrorModal();
        const closeListener = vi.fn();

        modal.addEventListener('close', closeListener);
        modal.initWithError(new Error('Unexpected'));

        expect(document.body.children).toHaveLength(0);

        const root = await openAndRender(modal);

        expect(document.body.children).toHaveLength(1);
        expect(root.querySelector('[role="dialog"]')?.getAttribute('aria-labelledby')).toBe('title');
        expect(root.getElementById('title')).not.toBeNull();

        modal.close();

        expect(document.body.children).toHaveLength(0);
        expect(closeListener).toHaveBeenCalledWith(undefined);
    });

    it('closes with the triggering event on Escape, ignoring other keys', async () => {
        const modal = new ErrorModal();
        const closeListener = vi.fn();

        modal.addEventListener('close', closeListener);
        modal.initWithError(new Error('Unexpected'));
        await openAndRender(modal);

        document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
        expect(closeListener).not.toHaveBeenCalled();

        const escapeEvent = new KeyboardEvent('keydown', { key: 'Escape' });
        document.dispatchEvent(escapeEvent);

        expect(document.body.children).toHaveLength(0);
        expect(closeListener).toHaveBeenCalledWith(escapeEvent);

        // Listeners are detached once the element leaves the page.
        document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
        expect(closeListener).toHaveBeenCalledTimes(1);
    });

    it('closes with the triggering event on backdrop click, close button click, and page load', async () => {
        const modal = new ErrorModal();
        const closeListener = vi.fn();

        modal.addEventListener('close', closeListener);
        modal.initWithError(new Error('Unexpected'));

        let root = await openAndRender(modal);
        root.querySelector<HTMLElement>('.backdrop')!.click();
        expect(closeListener).toHaveBeenLastCalledWith(expect.any(MouseEvent));

        root = await openAndRender(modal);
        root.querySelector<HTMLElement>('.close')!.click();
        expect(closeListener).toHaveBeenLastCalledWith(expect.any(MouseEvent));

        await openAndRender(modal);
        const loadEvent = new Event('load');
        window.dispatchEvent(loadEvent);
        expect(closeListener).toHaveBeenLastCalledWith(loadEvent);

        expect(closeListener).toHaveBeenCalledTimes(3);
        expect(document.body.children).toHaveLength(0);
    });

    it('moves focus into the dialog on open and restores it on close', async () => {
        const trigger = document.createElement('button');
        const modal = new ErrorModal();

        document.body.appendChild(trigger);
        trigger.focus();
        modal.initWithError(new Error('Unexpected'));

        const root = await openAndRender(modal);

        expect(root.activeElement).toBe(root.querySelector('[role="dialog"]'));

        modal.close();

        expect(document.activeElement).toBe(trigger);
    });

    it('stops notifying removed close listeners', () => {
        const modal = new EmbeddedLoadingSpinner();
        const closeListener = vi.fn();

        const removeListener = modal.addEventListener('close', closeListener);
        modal.init();
        removeListener();
        modal.close();

        expect(closeListener).not.toHaveBeenCalled();
    });

    it('loads the font stylesheet once', async () => {
        const first = new ErrorModal();
        const second = new ErrorModal();

        first.initWithError(new Error('first'));
        second.initWithError(new Error('second'));
        await openAndRender(first);
        await openAndRender(second);

        expect(document.head.querySelectorAll('#mobile-wallet-adapter-embedded-modal-font')).toHaveLength(1);
    });
});

describe('EmbeddedLoadingSpinner', () => {
    it('renders without a card and closes on backdrop click', async () => {
        const spinner = new EmbeddedLoadingSpinner();
        const closeListener = vi.fn();

        spinner.addEventListener('close', closeListener);
        spinner.init();
        spinner.init();

        const root = await openAndRender(spinner);

        expect(document.body.children).toHaveLength(1);
        expect(root.querySelector('.card')).toBeNull();
        expect(root.querySelector('.animation')).not.toBeNull();

        root.querySelector<HTMLElement>('.backdrop')!.click();

        expect(document.body.children).toHaveLength(0);
        expect(closeListener).toHaveBeenCalledWith(expect.any(MouseEvent));
    });
});

describe('ErrorModal', () => {
    it('shows the wallet-not-found message with a find-a-wallet action', async () => {
        const modal = new ErrorModal();
        const location = { href: '' };

        vi.stubGlobal('location', location);
        modal.initWithError(createMwaError('ERROR_WALLET_NOT_FOUND'));
        const root = await openAndRender(modal);

        expect(root.querySelector('.subtitle')?.textContent).toBe(
            'To use mobile wallet adapter, you must have a compatible mobile wallet application installed on your device.',
        );

        root.querySelector<HTMLElement>('.action')!.click();

        expect(window.location.href).toBe('https://solanamobile.com/wallets');
    });

    it('shows the browser-not-supported message without an action', async () => {
        const modal = new ErrorModal();

        modal.initWithError(createMwaError('ERROR_BROWSER_NOT_SUPPORTED'));
        const root = await openAndRender(modal);

        expect(root.querySelector('.subtitle')?.textContent).toBe(
            'This browser appears to be incompatible with mobile wallet adapter. Open this page in a compatible mobile browser app and try again.',
        );
        expect(root.querySelector('.action')).toBeNull();
    });

    it('renders unexpected error messages as text', async () => {
        const modal = new ErrorModal();

        modal.initWithError(new Error('<img src=x onerror=alert(1)>'));
        const root = await openAndRender(modal);

        expect(root.querySelector('.subtitle')?.textContent).toBe(
            'An unexpected error occurred: <img src=x onerror=alert(1)>',
        );
        expect(root.querySelector('img')).toBeNull();
        expect(root.querySelector('.action')).toBeNull();
    });
});

describe('LocalConnectionModal', () => {
    it('closes without an event and runs the callback once when Connect Wallet is tapped', async () => {
        const callback = vi.fn().mockResolvedValue(undefined);
        const closeListener = vi.fn();
        const modal = new LocalConnectionModal();

        modal.addEventListener('close', closeListener);
        modal.initWithCallback(callback);
        const root = await openAndRender(modal);

        expect(root.querySelector('.close')).toBeNull();

        const button = root.querySelector<HTMLElement>('.action')!;
        button.click();
        button.click();

        expect(callback).toHaveBeenCalledTimes(1);
        expect(closeListener).toHaveBeenCalledTimes(1);
        expect(closeListener).toHaveBeenCalledWith(undefined);
        expect(document.body.children).toHaveLength(0);
    });
});

describe('LoopbackPermissionModal', () => {
    it('requests local network permission once, then closes without an event', async () => {
        const fetchMock = vi.fn().mockRejectedValue(new Error('ignored'));
        const closeListener = vi.fn();
        const modal = new LoopbackPermissionModal();

        vi.stubGlobal('fetch', fetchMock);
        modal.addEventListener('close', closeListener);
        modal.init();
        const root = await openAndRender(modal);

        expect(root.querySelector('.close')).toBeNull();
        expect(root.querySelector('.permission-prompt-mock')).not.toBeNull();

        const button = root.querySelector<HTMLElement>('.action')!;
        button.click();
        button.click();

        await vi.waitFor(() => expect(closeListener).toHaveBeenCalledWith(undefined));

        expect(fetchMock).toHaveBeenCalledTimes(1);
        expect(fetchMock).toHaveBeenCalledWith('http://localhost');
        expect(closeListener).toHaveBeenCalledTimes(1);
    });
});

describe('LoopbackPermissionBlockedModal', () => {
    it.each([
        [true, 'Long press the app icon on your home screen to open site settings'],
        [false, 'Tap the lock or settings icon in the address bar to open site settings'],
    ])('shows the site settings instruction when launched as an app is %s', async (isPwa, instruction) => {
        mockGetIsPwaLaunchedAsApp.mockReturnValueOnce(isPwa);

        const modal = new LoopbackPermissionBlockedModal();
        modal.init();
        const root = await openAndRender(modal);

        expect(root.querySelector('.details-collapsible-content')?.textContent).toContain(instruction);
    });

    it('closes with the triggering event when Got it is tapped', async () => {
        mockGetIsPwaLaunchedAsApp.mockReturnValueOnce(false);

        const modal = new LoopbackPermissionBlockedModal();
        const closeListener = vi.fn();

        modal.addEventListener('close', closeListener);
        modal.init();
        const root = await openAndRender(modal);

        root.querySelector<HTMLElement>('.action')!.click();

        expect(closeListener).toHaveBeenCalledWith(expect.any(MouseEvent));
        expect(document.body.children).toHaveLength(0);
    });
});

describe('RemoteConnectionModal', () => {
    it('shows a placeholder until the QR code is ready, then replaces it on update', async () => {
        const firstCanvas = document.createElement('canvas');
        const secondCanvas = document.createElement('canvas');
        const modal = new RemoteConnectionModal();

        mockQrCodeToCanvas.mockResolvedValueOnce(firstCanvas).mockResolvedValueOnce(secondCanvas);

        modal.init();
        let root = await openAndRender(modal);

        expect(root.querySelector('.qr-placeholder')).not.toBeNull();
        expect(root.querySelector('.close')).not.toBeNull();

        await modal.populateQRCode('solana-wallet:/first');
        root = await rendered(modal);

        expect(mockQrCodeToCanvas).toHaveBeenCalledWith('solana-wallet:/first', { margin: 0, width: 200 });
        expect(root.querySelector('.qr-code-container')?.firstElementChild).toBe(firstCanvas);
        expect(root.querySelector('.qr-placeholder')).toBeNull();

        await modal.populateQRCode('solana-wallet:/second');
        root = await rendered(modal);

        expect(root.querySelector('.qr-code-container')?.firstElementChild).toBe(secondCanvas);
        expect(root.contains(firstCanvas)).toBe(false);
    });

    it('populates the QR code from initWithQR', async () => {
        const canvas = document.createElement('canvas');
        const modal = new RemoteConnectionModal();

        mockQrCodeToCanvas.mockResolvedValueOnce(canvas);

        await modal.initWithQR('solana-wallet:/init');
        const root = await openAndRender(modal);

        expect(root.querySelector('.qr-code-container')?.firstElementChild).toBe(canvas);
    });

    it('logs when the QR code is populated before initialization', async () => {
        const modal = new RemoteConnectionModal();
        const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => undefined);

        await modal.populateQRCode('solana-wallet:/missing-container');

        expect(consoleErrorSpy).toHaveBeenCalledWith('QRCode Container not found');
        expect(mockQrCodeToCanvas).not.toHaveBeenCalled();
    });
});

function createMwaError(code: 'ERROR_BROWSER_NOT_SUPPORTED' | 'ERROR_WALLET_NOT_FOUND') {
    return {
        code,
        message: '',
        name: 'SolanaMobileWalletAdapterError',
    } as Error;
}

async function rendered(modal: EmbeddedModal): Promise<ShadowRoot> {
    const element = (modal as unknown as { element: (HTMLElement & { updateComplete: Promise<boolean> }) | null })
        .element;

    if (!element) {
        throw new Error('Expected modal element to be initialized');
    }

    await element.updateComplete;
    // The shadow root is closed, so it is only reachable through Lit's renderRoot.
    return (element as unknown as { renderRoot: ShadowRoot }).renderRoot;
}

async function openAndRender(modal: EmbeddedModal): Promise<ShadowRoot> {
    modal.open();
    return rendered(modal);
}
