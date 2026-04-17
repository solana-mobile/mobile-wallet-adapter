import { afterEach, describe, expect, it, vi } from 'vitest';

const { mockAddListener, mockRemove, nativeModules, platform } = vi.hoisted(() => ({
    mockAddListener: vi.fn(),
    mockRemove: vi.fn(),
    nativeModules: {
        SolanaMobileWalletAdapterWalletLib: {
            resolve: vi.fn(),
        },
    },
    platform: { OS: 'android' },
}));

vi.mock('react-native', () => ({
    NativeModules: nativeModules,
    NativeEventEmitter: class {
        addListener = mockAddListener;
    },
    Platform: platform,
}));

import { initializeMWAEventListener } from '../src/initializeMWAEventListener.js';
import { MWASessionEventType } from '../src/mwaSessionEvents.js';
import { MWARequestType } from '../src/resolve.js';

afterEach(() => {
    mockAddListener.mockReset();
    mockRemove.mockReset();
    platform.OS = 'android';
    vi.restoreAllMocks();
});

describe('initializeMWAEventListener', () => {
    it('routes native requests to the request handler', () => {
        const handleRequest = vi.fn();
        const handleSessionEvent = vi.fn();
        const request = {
            __type: MWARequestType.AuthorizeDappRequest,
            chain: 'solana:mainnet',
            requestId: 'request-1',
            sessionId: 'session-1',
        };

        mockAddListener.mockImplementation((_eventName, listener) => {
            listener(request);

            return { remove: mockRemove };
        });

        const listener = initializeMWAEventListener(handleRequest, handleSessionEvent);

        expect(mockAddListener).toHaveBeenCalledWith('MobileWalletAdapterServiceRequestBridge', expect.any(Function));
        expect(handleRequest).toHaveBeenCalledWith(request);
        expect(handleSessionEvent).not.toHaveBeenCalled();
        expect(listener.remove).toBe(mockRemove);
    });

    it('routes native session events to the session-event handler', () => {
        const handleRequest = vi.fn();
        const handleSessionEvent = vi.fn();
        const sessionEvent = {
            __type: MWASessionEventType.SessionReadyEvent,
            sessionId: 'session-1',
        };

        mockAddListener.mockImplementation((_eventName, listener) => {
            listener(sessionEvent);

            return { remove: mockRemove };
        });

        initializeMWAEventListener(handleRequest, handleSessionEvent);

        expect(handleRequest).not.toHaveBeenCalled();
        expect(handleSessionEvent).toHaveBeenCalledWith(sessionEvent);
    });

    it('warns when the native event type is unknown', () => {
        const handleRequest = vi.fn();
        const handleSessionEvent = vi.fn();
        const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});

        mockAddListener.mockImplementation((_eventName, listener) => {
            listener({ __type: 'UNKNOWN_EVENT' });

            return { remove: mockRemove };
        });

        initializeMWAEventListener(handleRequest, handleSessionEvent);

        expect(handleRequest).not.toHaveBeenCalled();
        expect(handleSessionEvent).not.toHaveBeenCalled();
        expect(warnSpy).toHaveBeenCalledWith('Unexpected native event type');
    });
});
