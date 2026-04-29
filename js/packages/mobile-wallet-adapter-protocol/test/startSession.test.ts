import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const ASSOCIATION_PORT = 51234;
const ASSOCIATION_PUBLIC_KEY = {} as CryptoKey;
const ASSOCIATION_URL_BASE = 'https://wallet.example';

const { mockGetAssociateAndroidIntentURL, mockGetRandomAssociationPort } = vi.hoisted(() => ({
    mockGetAssociateAndroidIntentURL: vi.fn(),
    mockGetRandomAssociationPort: vi.fn(),
}));

vi.mock('../src/associationPort.js', () => ({
    getRandomAssociationPort: mockGetRandomAssociationPort,
}));

vi.mock('../src/getAssociateAndroidIntentURL.js', () => ({
    default: mockGetAssociateAndroidIntentURL,
}));

import { SolanaMobileWalletAdapterErrorCode } from '../src/errors.js';
import { startSession } from '../src/startSession.js';

beforeEach(() => {
    mockGetRandomAssociationPort.mockReturnValue(ASSOCIATION_PORT);
});

afterEach(() => {
    mockGetAssociateAndroidIntentURL.mockReset();
    mockGetRandomAssociationPort.mockReset();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    vi.useRealTimers();
});

describe('startSession', () => {
    it('launches https association URLs directly', async () => {
        const associationUrl = new URL('https://wallet.example/v1/associate/local');
        const mockAssign = vi.fn();
        mockGetAssociateAndroidIntentURL.mockResolvedValue(associationUrl);
        vi.stubGlobal('window', {
            location: {
                assign: mockAssign,
            },
        });

        await expect(startSession(ASSOCIATION_PUBLIC_KEY, ASSOCIATION_URL_BASE)).resolves.toBe(ASSOCIATION_PORT);

        expect(mockAssign).toHaveBeenCalledWith(associationUrl);
        expect(mockGetAssociateAndroidIntentURL).toHaveBeenCalledWith(
            ASSOCIATION_PUBLIC_KEY,
            ASSOCIATION_PORT,
            ASSOCIATION_URL_BASE,
        );
    });

    it('launches custom protocol URLs through a hidden iframe in Firefox', async () => {
        const associationUrl = new URL('solana-wallet:/v1/associate/local');
        const frame = {
            contentWindow: {
                location: {
                    href: '',
                },
            },
            style: {
                display: '',
            },
        };
        const mockAppendChild = vi.fn();
        const mockCreateElement = vi.fn(() => frame);
        mockGetAssociateAndroidIntentURL.mockResolvedValue(associationUrl);
        vi.stubGlobal('document', {
            body: {
                appendChild: mockAppendChild,
            },
            createElement: mockCreateElement,
        });
        vi.stubGlobal('navigator', {
            userAgent: 'Firefox/123',
        });

        await expect(startSession(ASSOCIATION_PUBLIC_KEY)).resolves.toBe(ASSOCIATION_PORT);

        expect(frame.contentWindow.location.href).toBe(associationUrl.toString());
        expect(frame.style.display).toBe('none');
        expect(mockAppendChild).toHaveBeenCalledWith(frame);
        expect(mockCreateElement).toHaveBeenCalledWith('iframe');
    });

    it('waits for blur after assigning custom protocol URLs in other browsers', async () => {
        const associationUrl = new URL('solana-wallet:/v1/associate/local');
        let blurHandler: (() => void) | undefined;
        const mockAddEventListener = vi.fn((eventName: string, handler: () => void) => {
            if (eventName === 'blur') {
                blurHandler = handler;
            }
        });
        const mockAssign = vi.fn(() => {
            blurHandler?.();
        });
        const mockRemoveEventListener = vi.fn();
        mockGetAssociateAndroidIntentURL.mockResolvedValue(associationUrl);
        vi.stubGlobal('navigator', {
            userAgent: 'Chrome/123',
        });
        vi.stubGlobal('window', {
            addEventListener: mockAddEventListener,
            location: {
                assign: mockAssign,
            },
            removeEventListener: mockRemoveEventListener,
        });

        await expect(startSession(ASSOCIATION_PUBLIC_KEY)).resolves.toBe(ASSOCIATION_PORT);

        expect(mockAddEventListener).toHaveBeenCalledWith('blur', expect.any(Function));
        expect(mockAssign).toHaveBeenCalledWith(associationUrl);
        expect(mockRemoveEventListener).toHaveBeenCalledWith('blur', blurHandler);
    });

    it('rejects when a custom protocol URL does not navigate away', async () => {
        const associationUrl = new URL('solana-wallet:/v1/associate/local');
        const mockAddEventListener = vi.fn();
        const mockAssign = vi.fn();
        const mockRemoveEventListener = vi.fn();
        mockGetAssociateAndroidIntentURL.mockResolvedValue(associationUrl);
        vi.stubGlobal('navigator', {
            userAgent: 'Chrome/123',
        });
        vi.stubGlobal('window', {
            addEventListener: mockAddEventListener,
            location: {
                assign: mockAssign,
            },
            removeEventListener: mockRemoveEventListener,
        });
        vi.useFakeTimers();

        const sessionPromise = startSession(ASSOCIATION_PUBLIC_KEY);
        const expectation = expect(sessionPromise).rejects.toEqual(
            expect.objectContaining({
                code: SolanaMobileWalletAdapterErrorCode.ERROR_WALLET_NOT_FOUND,
                name: 'SolanaMobileWalletAdapterError',
            }),
        );
        await vi.advanceTimersByTimeAsync(3000);
        await expectation;

        expect(mockAssign).toHaveBeenCalledWith(associationUrl);
        expect(mockRemoveEventListener).toHaveBeenCalledWith('blur', expect.any(Function));
    });
});
