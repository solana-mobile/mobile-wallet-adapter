// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { fromUint8Array } from '../src/base64Utils.js';
import { ENCODED_PUBLIC_KEY_LENGTH_BYTES } from '../src/encryptedMessage.js';

const ASSOCIATION_KEYPAIR = {
    privateKey: {} as CryptoKey,
    publicKey: {} as CryptoKey,
} as const satisfies CryptoKeyPair;
const ASSOCIATION_PORT = 51234;
const ASSOCIATION_URL = new URL('https://wallet.example/associate');
const ECDH_KEYPAIR = {
    privateKey: {} as CryptoKey,
    publicKey: {} as CryptoKey,
} as const satisfies CryptoKeyPair;
const ENCRYPTED_JSON_RPC_MESSAGE = Uint8Array.of(9, 10, 11);
const HELLO_REQ = Uint8Array.of(1, 2, 3);
const HELLO_RSP = Uint8Array.from({ length: ENCODED_PUBLIC_KEY_LENGTH_BYTES }, (_, index) => index);
const INBOUND_SEQUENCE_ONE = Uint8Array.of(0, 0, 0, 1, 99);
const SHARED_SECRET = {} as CryptoKey;
const WALLET = {} as const;

const {
    mockCreateHelloReq,
    mockCreateMobileWalletProxy,
    mockCreateNostrEvent,
    mockDecryptJsonRpcMessage,
    mockDeriveSessionIdentifier,
    mockEncryptJsonRpcMessage,
    mockGenerateAssociationKeypair,
    mockGenerateECDHKeypair,
    mockGenerateNostrKeypair,
    mockGetNostrAssociateAndroidIntentURL,
    mockGetRemoteAssociateAndroidIntentURL,
    mockParseHelloRsp,
    mockParseSessionProps,
    mockStartNostrSession,
    mockStartSession,
    mockVerifyNostrEvent,
} = vi.hoisted(() => ({
    mockCreateHelloReq: vi.fn(),
    mockCreateMobileWalletProxy: vi.fn(),
    mockCreateNostrEvent: vi.fn(),
    mockDecryptJsonRpcMessage: vi.fn(),
    mockDeriveSessionIdentifier: vi.fn(),
    mockEncryptJsonRpcMessage: vi.fn(),
    mockGenerateAssociationKeypair: vi.fn(),
    mockGenerateECDHKeypair: vi.fn(),
    mockGenerateNostrKeypair: vi.fn(),
    mockGetNostrAssociateAndroidIntentURL: vi.fn(),
    mockGetRemoteAssociateAndroidIntentURL: vi.fn(),
    mockParseHelloRsp: vi.fn(),
    mockParseSessionProps: vi.fn(),
    mockStartNostrSession: vi.fn(),
    mockStartSession: vi.fn(),
    mockVerifyNostrEvent: vi.fn(),
}));

vi.mock('../src/createHelloReq.js', () => ({
    default: mockCreateHelloReq,
}));

vi.mock('../src/createMobileWalletProxy.js', () => ({
    default: mockCreateMobileWalletProxy,
}));

vi.mock('../src/generateAssociationKeypair.js', () => ({
    default: mockGenerateAssociationKeypair,
}));

vi.mock('../src/generateECDHKeypair.js', () => ({
    default: mockGenerateECDHKeypair,
}));

vi.mock('../src/getAssociateAndroidIntentURL.js', () => ({
    getNostrAssociateAndroidIntentURL: mockGetNostrAssociateAndroidIntentURL,
    getRemoteAssociateAndroidIntentURL: mockGetRemoteAssociateAndroidIntentURL,
}));

vi.mock('../src/jsonRpcMessage.js', () => ({
    decryptJsonRpcMessage: mockDecryptJsonRpcMessage,
    encryptJsonRpcMessage: mockEncryptJsonRpcMessage,
}));

vi.mock('../src/nostr.js', () => ({
    createNostrEvent: mockCreateNostrEvent,
    deriveSessionIdentifier: mockDeriveSessionIdentifier,
    generateNostrKeypair: mockGenerateNostrKeypair,
    NOSTR_EVENT_KIND_MWA: 20012,
    verifyNostrEvent: mockVerifyNostrEvent,
}));

vi.mock('../src/parseHelloRsp.js', () => ({
    default: mockParseHelloRsp,
}));

vi.mock('../src/parseSessionProps.js', () => ({
    default: mockParseSessionProps,
}));

vi.mock('../src/startSession.js', () => ({
    startSession: mockStartSession,
    startNostrSession: mockStartNostrSession,
}));

import { SolanaMobileWalletAdapterErrorCode, SolanaMobileWalletAdapterProtocolError } from '../src/errors.js';
import { startNostrScenario, startRemoteScenario, startScenario, transact } from '../src/transact.js';
import { NostrWalletAssociationConfig, RemoteScenario, RemoteWalletAssociationConfig } from '../src/types.js';

beforeEach(() => {
    MockWebSocket.instances = [];
    mockCreateHelloReq.mockResolvedValue(HELLO_REQ);
    mockCreateMobileWalletProxy.mockReturnValue(WALLET);
    mockEncryptJsonRpcMessage.mockResolvedValue(ENCRYPTED_JSON_RPC_MESSAGE);
    mockGenerateAssociationKeypair.mockResolvedValue(ASSOCIATION_KEYPAIR);
    mockGenerateECDHKeypair.mockResolvedValue(ECDH_KEYPAIR);
    mockGetRemoteAssociateAndroidIntentURL.mockResolvedValue(ASSOCIATION_URL);
    mockParseHelloRsp.mockResolvedValue(SHARED_SECRET);
    mockParseSessionProps.mockResolvedValue({ protocol_version: 'v1' });
    mockStartSession.mockResolvedValue(ASSOCIATION_PORT);
    mockStartNostrSession.mockResolvedValue(ASSOCIATION_URL);
    mockCreateNostrEvent.mockImplementation(
        (_kind: number, content: string, tags: string[][], _privateKey: Uint8Array) => ({
            id: 'mock-event-id',
            pubkey: 'dapp-nostr-pubkey',
            created_at: 1000,
            kind: 20012,
            tags,
            content,
            sig: 'mock-sig',
        }),
    );
    mockDeriveSessionIdentifier.mockResolvedValue('mock-session-id');
    mockGenerateNostrKeypair.mockReturnValue({ privateKey: new Uint8Array([1, 2, 3]), publicKey: 'dapp-nostr-pubkey' });
    mockVerifyNostrEvent.mockReturnValue(true);
    Object.defineProperty(window, 'isSecureContext', {
        configurable: true,
        value: true,
    });
    vi.stubGlobal('WebSocket', MockWebSocket);
});

afterEach(() => {
    mockCreateHelloReq.mockReset();
    mockCreateMobileWalletProxy.mockReset();
    mockDecryptJsonRpcMessage.mockReset();
    mockEncryptJsonRpcMessage.mockReset();
    mockGenerateAssociationKeypair.mockReset();
    mockGenerateECDHKeypair.mockReset();
    mockGetRemoteAssociateAndroidIntentURL.mockReset();
    mockParseHelloRsp.mockReset();
    mockParseSessionProps.mockReset();
    mockStartSession.mockReset();
    mockStartNostrSession.mockReset();
    mockCreateNostrEvent.mockReset();
    mockDeriveSessionIdentifier.mockReset();
    mockGenerateNostrKeypair.mockReset();
    mockVerifyNostrEvent.mockReset();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    vi.useRealTimers();
});

describe('transact', () => {
    it('closes the local scenario after the callback resolves', async () => {
        const callback = vi.fn().mockResolvedValue('result');

        const resultPromise = transact(callback, { baseUri: 'https://wallet.example' });
        await flushPromises();
        const socket = getOnlySocket();
        await establishLocalSession(socket);

        await expect(resultPromise).resolves.toBe('result');

        expect(callback).toHaveBeenCalledWith(WALLET);
        expect(socket.close).toHaveBeenCalledTimes(1);
    });
});

describe('startScenario', () => {
    it('requires a secure browser context', async () => {
        Object.defineProperty(window, 'isSecureContext', {
            configurable: true,
            value: false,
        });

        await expect(startScenario()).rejects.toEqual(
            expect.objectContaining({
                code: SolanaMobileWalletAdapterErrorCode.ERROR_SECURE_CONTEXT_REQUIRED,
                name: 'SolanaMobileWalletAdapterError',
            }),
        );
    });

    it('establishes a local wallet session and resolves JSON-RPC results', async () => {
        const scenario = await startScenario({ baseUri: 'https://wallet.example' });
        const socket = getOnlySocket();
        await establishLocalSession(socket);

        await expect(scenario.wallet).resolves.toBe(WALLET);

        const requestHandler = getLastProtocolRequestHandler();
        const responseResult = {
            accounts: [],
            auth_token: 'auth-token',
            wallet_uri_base: 'https://wallet.example',
        };
        const responsePromise = requestHandler('authorize', { identity: { name: 'Test App' } });
        await flushPromises();

        expect(mockEncryptJsonRpcMessage).toHaveBeenCalledWith(
            {
                id: 1,
                jsonrpc: '2.0',
                method: 'authorize',
                params: { identity: { name: 'Test App' } },
            },
            SHARED_SECRET,
        );
        expect(socket.sent).toEqual([HELLO_REQ, ENCRYPTED_JSON_RPC_MESSAGE]);

        mockDecryptJsonRpcMessage.mockResolvedValue({
            id: 1,
            jsonrpc: '2.0',
            result: responseResult,
        });
        await socket.dispatch('message', createBlobMessageEvent(INBOUND_SEQUENCE_ONE));

        await expect(responsePromise).resolves.toBe(responseResult);
    });

    it('uses empty params for local JSON-RPC requests without inputs', async () => {
        const scenario = await startScenario();
        const socket = getOnlySocket();
        await establishLocalSession(socket);
        await scenario.wallet;

        const requestHandler = getLastProtocolRequestHandler();
        const responseResult = {
            features: [],
        };
        const responsePromise = requestHandler('get_capabilities');
        await flushPromises();

        expect(mockEncryptJsonRpcMessage).toHaveBeenCalledWith(
            {
                id: 1,
                jsonrpc: '2.0',
                method: 'get_capabilities',
                params: {},
            },
            SHARED_SECRET,
        );

        mockDecryptJsonRpcMessage.mockResolvedValue({
            id: 1,
            jsonrpc: '2.0',
            result: responseResult,
        });
        await socket.dispatch('message', createBlobMessageEvent(INBOUND_SEQUENCE_ONE));

        await expect(responsePromise).resolves.toBe(responseResult);
    });

    it('rejects the wallet promise when the local socket closes unexpectedly', async () => {
        const scenario = await startScenario();
        const socket = getOnlySocket();
        const walletPromise = scenario.wallet;

        await socket.dispatch(
            'close',
            new CloseEvent('close', {
                code: 1006,
                reason: 'dropped',
                wasClean: false,
            }),
        );

        await expect(walletPromise).rejects.toEqual(
            expect.objectContaining({
                code: SolanaMobileWalletAdapterErrorCode.ERROR_SESSION_CLOSED,
                name: 'SolanaMobileWalletAdapterError',
            }),
        );
    });

    it('rejects the wallet promise when the local socket connection times out', async () => {
        vi.useFakeTimers();
        vi.setSystemTime(0);
        const scenario = await startScenario();
        const socket = getOnlySocket();
        const walletPromise = scenario.wallet;

        vi.setSystemTime(30000);
        await socket.dispatch('error', new Event('error'));

        await expect(walletPromise).rejects.toEqual(
            expect.objectContaining({
                code: SolanaMobileWalletAdapterErrorCode.ERROR_SESSION_TIMEOUT,
                name: 'SolanaMobileWalletAdapterError',
            }),
        );
    });

    it('retries local socket connections before the timeout expires', async () => {
        vi.useFakeTimers();
        vi.setSystemTime(0);
        const scenario = await startScenario();
        const firstSocket = getOnlySocket();

        const dispatchPromise = firstSocket.dispatch('error', new Event('error'));
        await vi.advanceTimersByTimeAsync(150);
        await dispatchPromise;

        expect(MockWebSocket.instances).toHaveLength(2);
        const secondSocket = MockWebSocket.instances[1];
        await establishLocalSession(secondSocket);

        await expect(scenario.wallet).resolves.toBe(WALLET);
    });

    it('sends the local hello request after an app ping while connecting', async () => {
        const scenario = await startScenario();
        const socket = getOnlySocket();

        await socket.dispatch('message', createBlobMessageEvent(Uint8Array.of()));
        await socket.dispatch('message', createBlobMessageEvent(HELLO_RSP));

        await expect(scenario.wallet).resolves.toBe(WALLET);
        expect(socket.sent).toEqual([HELLO_REQ]);
    });

    it('resends the local hello request after an app ping while waiting for hello response', async () => {
        const scenario = await startScenario();
        const socket = getOnlySocket();

        await socket.dispatch('open', new Event('open'));
        await socket.dispatch('message', createBlobMessageEvent(Uint8Array.of()));
        await socket.dispatch('message', createBlobMessageEvent(HELLO_RSP));

        await expect(scenario.wallet).resolves.toBe(WALLET);
        expect(socket.sent).toEqual([HELLO_REQ, HELLO_REQ]);
    });

    it('throws when the local wallet sends a non-empty app ping while connecting', async () => {
        const scenario = await startScenario();
        const socket = getOnlySocket();

        await expect(socket.dispatch('message', createBlobMessageEvent(Uint8Array.of(1)))).resolves.toBeUndefined();
        await expect(Promise.race([scenario.wallet, Promise.resolve('pending')])).rejects.toThrow(
            'Encountered unexpected message while connecting',
        );
    });

    it('throws when local encrypted messages arrive out of sequence', async () => {
        const scenario = await startScenario();
        const socket = getOnlySocket();
        await establishLocalSession(socket);
        await scenario.wallet;

        await expect(socket.dispatch('message', createBlobMessageEvent(Uint8Array.of(0, 0, 0, 2, 99)))).rejects.toThrow(
            'Encrypted message has invalid sequence number',
        );
    });

    it('throws when local session properties arrive out of sequence', async () => {
        const scenario = await startScenario();
        const socket = getOnlySocket();

        await socket.dispatch('open', new Event('open'));

        await expect(
            socket.dispatch('message', createBlobMessageEvent(Uint8Array.of(...HELLO_RSP, 0, 0, 0, 2, 12))),
        ).resolves.toBeUndefined();
        await expect(scenario.wallet).rejects.toThrow('Encrypted message has invalid sequence number');
    });

    it('parses local session properties from hello responses', async () => {
        const scenario = await startScenario();
        const socket = getOnlySocket();
        const sessionPropertiesBuffer = Uint8Array.of(0, 0, 0, 1, 12);

        await socket.dispatch('open', new Event('open'));
        await socket.dispatch(
            'message',
            createBlobMessageEvent(Uint8Array.of(...HELLO_RSP, ...sessionPropertiesBuffer)),
        );

        await expect(scenario.wallet).resolves.toBe(WALLET);
        expect(mockParseSessionProps).toHaveBeenCalledWith(expect.any(ArrayBuffer), SHARED_SECRET);
        expect(new Uint8Array(mockParseSessionProps.mock.calls[0][0] as ArrayBuffer)).toEqual(sessionPropertiesBuffer);
        expect(mockCreateMobileWalletProxy).toHaveBeenCalledWith('v1', expect.any(Function));
    });

    it('rejects pending local RPC requests when the wallet returns protocol errors', async () => {
        const scenario = await startScenario();
        const socket = getOnlySocket();
        await establishLocalSession(socket);
        await scenario.wallet;

        const protocolError = new SolanaMobileWalletAdapterProtocolError(1, -3, 'not signed');
        const requestHandler = getLastProtocolRequestHandler();
        const responsePromise = requestHandler('sign_messages', {
            addresses: [],
            payloads: [],
        });
        await flushPromises();

        mockDecryptJsonRpcMessage.mockRejectedValue(protocolError);
        await socket.dispatch('message', createBlobMessageEvent(INBOUND_SEQUENCE_ONE));

        await expect(responsePromise).rejects.toBe(protocolError);
    });

    it('rejects authorization responses with insecure wallet base URLs', async () => {
        const scenario = await startScenario();
        const socket = getOnlySocket();
        await establishLocalSession(socket);
        await scenario.wallet;

        const requestHandler = getLastProtocolRequestHandler();
        const responsePromise = requestHandler('authorize', { identity: { name: 'Test App' } });
        await flushPromises();

        mockDecryptJsonRpcMessage.mockResolvedValue({
            id: 1,
            jsonrpc: '2.0',
            result: {
                accounts: [],
                auth_token: 'auth-token',
                wallet_uri_base: 'http://wallet.example',
            },
        });
        await socket.dispatch('message', createBlobMessageEvent(INBOUND_SEQUENCE_ONE));

        await expect(responsePromise).rejects.toEqual(
            expect.objectContaining({
                code: SolanaMobileWalletAdapterErrorCode.ERROR_FORBIDDEN_WALLET_BASE_URL,
                name: 'SolanaMobileWalletAdapterError',
            }),
        );
    });

    it('rejects the wallet promise when the local scenario is closed before connection', async () => {
        const scenario = await startScenario();
        const socket = getOnlySocket();

        scenario.close();

        await expect(scenario.wallet).rejects.toEqual(
            expect.objectContaining({
                code: SolanaMobileWalletAdapterErrorCode.ERROR_SESSION_CLOSED,
                name: 'SolanaMobileWalletAdapterError',
            }),
        );
        expect(socket.close).toHaveBeenCalledTimes(1);
    });
});

describe('startRemoteScenario', () => {
    it('establishes a base64 remote session through the reflector', async () => {
        const scenarioPromise = startRemoteScenario(createRemoteConfig());
        await flushPromises();
        const socket = getOnlySocket();
        socket.protocol = 'com.solana.mobilewalletadapter.v1.base64';

        await socket.dispatch('open', new Event('open'));
        await socket.dispatch('message', createStringMessageEvent(fromUint8Array(Uint8Array.of(3, 7, 8, 9))));

        const scenario = await scenarioPromise;
        expect(scenario.associationUrl).toBe(ASSOCIATION_URL);
        expect(mockGetRemoteAssociateAndroidIntentURL).toHaveBeenCalledWith(
            ASSOCIATION_KEYPAIR.publicKey,
            'reflector.example',
            Uint8Array.of(7, 8, 9),
            'https://wallet.example',
        );

        const walletPromise = scenario.wallet;
        await socket.dispatch('message', createStringMessageEvent(''));
        await socket.dispatch('message', createStringMessageEvent(fromUint8Array(HELLO_RSP)));

        await expect(walletPromise).resolves.toBe(WALLET);
        const requestHandler = getLastProtocolRequestHandler();
        const responseResult = {
            signed_payloads: [],
        };
        const responsePromise = requestHandler('sign_messages', {
            addresses: [],
            payloads: [],
        });
        await flushPromises();

        expect(socket.sent).toEqual([fromUint8Array(HELLO_REQ), fromUint8Array(ENCRYPTED_JSON_RPC_MESSAGE)]);

        mockDecryptJsonRpcMessage.mockResolvedValue({
            id: 1,
            jsonrpc: '2.0',
            result: responseResult,
        });
        await socket.dispatch('message', createStringMessageEvent(fromUint8Array(INBOUND_SEQUENCE_ONE)));

        await expect(responsePromise).resolves.toBe(responseResult);
    });

    it('establishes a binary remote session and sends binary RPC messages', async () => {
        const scenarioPromise = startRemoteScenario(createRemoteConfig());
        await flushPromises();
        const socket = getOnlySocket();

        await socket.dispatch('open', new Event('open'));
        await socket.dispatch('message', createBlobMessageEvent(Uint8Array.of(3, 7, 8, 9)));

        const scenario = await scenarioPromise;
        const walletPromise = scenario.wallet;
        await socket.dispatch('message', createBlobMessageEvent(Uint8Array.of()));
        await socket.dispatch('message', createBlobMessageEvent(HELLO_RSP));

        await expect(walletPromise).resolves.toBe(WALLET);
        const requestHandler = getLastProtocolRequestHandler();
        const responseResult = {
            signed_payloads: [],
        };
        const responsePromise = requestHandler('sign_transactions', {
            payloads: [],
        });
        await flushPromises();

        expect(socket.sent).toEqual([HELLO_REQ, ENCRYPTED_JSON_RPC_MESSAGE]);

        mockDecryptJsonRpcMessage.mockResolvedValue({
            id: 1,
            jsonrpc: '2.0',
            result: responseResult,
        });
        await socket.dispatch('message', createBlobMessageEvent(INBOUND_SEQUENCE_ONE));

        await expect(responsePromise).resolves.toBe(responseResult);
    });

    it('retries remote reflector socket connections before the timeout expires', async () => {
        vi.useFakeTimers();
        vi.setSystemTime(0);
        const scenarioPromise = startRemoteScenario(createRemoteConfig());
        await flushPromises();
        const firstSocket = getOnlySocket();

        const dispatchPromise = firstSocket.dispatch('error', new Event('error'));
        await vi.advanceTimersByTimeAsync(150);
        await dispatchPromise;

        expect(MockWebSocket.instances).toHaveLength(2);
        const secondSocket = MockWebSocket.instances[1];
        await secondSocket.dispatch('open', new Event('open'));
        await secondSocket.dispatch('message', createBlobMessageEvent(Uint8Array.of(3, 7, 8, 9)));

        await expect(scenarioPromise).resolves.toEqual(
            expect.objectContaining({
                associationUrl: ASSOCIATION_URL,
            }),
        );
    });

    it('rejects when the remote reflector socket connection times out', async () => {
        vi.useFakeTimers();
        vi.setSystemTime(0);
        const scenarioPromise = startRemoteScenario(createRemoteConfig());
        await flushPromises();
        const socket = getOnlySocket();

        vi.setSystemTime(30000);
        await socket.dispatch('error', new Event('error'));

        await expect(scenarioPromise).rejects.toEqual(
            expect.objectContaining({
                code: SolanaMobileWalletAdapterErrorCode.ERROR_SESSION_TIMEOUT,
                name: 'SolanaMobileWalletAdapterError',
            }),
        );
    });

    it('leaves the remote association pending when the reflector closes cleanly', async () => {
        const scenarioPromise = startRemoteScenario(createRemoteConfig());
        await flushPromises();
        const socket = getOnlySocket();

        await socket.dispatch(
            'close',
            new CloseEvent('close', {
                wasClean: true,
            }),
        );

        await expect(Promise.race([scenarioPromise, Promise.resolve('pending')])).resolves.toBe('pending');
    });

    it('throws when the reflector sends an empty id message while connecting', async () => {
        const scenarioPromise = startRemoteScenario(createRemoteConfig());
        await flushPromises();
        const socket = getOnlySocket();

        await socket.dispatch('open', new Event('open'));

        await expect(socket.dispatch('message', createBlobMessageEvent(Uint8Array.of()))).resolves.toBeUndefined();
        await expect(scenarioPromise).rejects.toThrow('Encountered unexpected message while connecting');
    });

    it('throws when remote wallet reflection receives an unexpected payload', async () => {
        const scenarioPromise = startRemoteScenario(createRemoteConfig());
        await flushPromises();
        const socket = getOnlySocket();

        await socket.dispatch('open', new Event('open'));
        await socket.dispatch('message', createBlobMessageEvent(Uint8Array.of(3, 7, 8, 9)));

        const scenario = await scenarioPromise;

        await expect(socket.dispatch('message', createBlobMessageEvent(Uint8Array.of(1)))).resolves.toBeUndefined();
        await expect(scenario.wallet).rejects.toThrow('Encountered unexpected message while awaiting reflection');
    });

    it('parses remote session properties from hello responses', async () => {
        const scenarioPromise = startRemoteScenario(createRemoteConfig());
        await flushPromises();
        const socket = getOnlySocket();
        const sessionPropertiesBuffer = Uint8Array.of(0, 0, 0, 1, 12);

        await socket.dispatch('open', new Event('open'));
        await socket.dispatch('message', createBlobMessageEvent(Uint8Array.of(3, 7, 8, 9)));

        const scenario = await scenarioPromise;
        await socket.dispatch('message', createBlobMessageEvent(Uint8Array.of()));
        await socket.dispatch(
            'message',
            createBlobMessageEvent(Uint8Array.of(...HELLO_RSP, ...sessionPropertiesBuffer)),
        );

        await expect(scenario.wallet).resolves.toBe(WALLET);
        expect(mockParseSessionProps).toHaveBeenCalledWith(expect.any(ArrayBuffer), SHARED_SECRET);
        expect(new Uint8Array(mockParseSessionProps.mock.calls[0][0] as ArrayBuffer)).toEqual(sessionPropertiesBuffer);
    });

    it('rejects pending remote RPC requests when the wallet returns protocol errors', async () => {
        const scenarioPromise = startRemoteScenario(createRemoteConfig());
        await flushPromises();
        const socket = getOnlySocket();

        await socket.dispatch('open', new Event('open'));
        await socket.dispatch('message', createBlobMessageEvent(Uint8Array.of(3, 7, 8, 9)));

        const scenario = await scenarioPromise;
        const walletPromise = scenario.wallet;
        await socket.dispatch('message', createBlobMessageEvent(Uint8Array.of()));
        await socket.dispatch('message', createBlobMessageEvent(HELLO_RSP));
        await walletPromise;

        const protocolError = new SolanaMobileWalletAdapterProtocolError(1, -3, 'not signed');
        const requestHandler = getLastProtocolRequestHandler();
        const responsePromise = requestHandler('sign_messages', {
            addresses: [],
            payloads: [],
        });
        await flushPromises();

        mockDecryptJsonRpcMessage.mockRejectedValue(protocolError);
        await socket.dispatch('message', createBlobMessageEvent(INBOUND_SEQUENCE_ONE));

        await expect(responsePromise).rejects.toBe(protocolError);
    });

    it('throws when remote encrypted messages arrive out of sequence', async () => {
        const scenarioPromise = startRemoteScenario(createRemoteConfig());
        await flushPromises();
        const socket = getOnlySocket();

        await socket.dispatch('open', new Event('open'));
        await socket.dispatch('message', createBlobMessageEvent(Uint8Array.of(3, 7, 8, 9)));

        const scenario = await scenarioPromise;
        const walletPromise = scenario.wallet;
        await socket.dispatch('message', createBlobMessageEvent(Uint8Array.of()));
        await socket.dispatch('message', createBlobMessageEvent(HELLO_RSP));
        await walletPromise;

        await expect(socket.dispatch('message', createBlobMessageEvent(Uint8Array.of(0, 0, 0, 2, 99)))).rejects.toThrow(
            'Encrypted message has invalid sequence number',
        );
    });

    it('throws non-protocol remote decrypt errors', async () => {
        const scenarioPromise = startRemoteScenario(createRemoteConfig());
        await flushPromises();
        const socket = getOnlySocket();

        await socket.dispatch('open', new Event('open'));
        await socket.dispatch('message', createBlobMessageEvent(Uint8Array.of(3, 7, 8, 9)));

        const scenario = await scenarioPromise;
        const walletPromise = scenario.wallet;
        await socket.dispatch('message', createBlobMessageEvent(Uint8Array.of()));
        await socket.dispatch('message', createBlobMessageEvent(HELLO_RSP));
        await walletPromise;

        const thrownError = new Error('decrypt failed');
        mockDecryptJsonRpcMessage.mockRejectedValue(thrownError);

        await expect(socket.dispatch('message', createBlobMessageEvent(INBOUND_SEQUENCE_ONE))).rejects.toBe(
            thrownError,
        );
    });

    it('rejects remote authorization responses with insecure wallet base URLs', async () => {
        const scenarioPromise = startRemoteScenario(createRemoteConfig());
        await flushPromises();
        const socket = getOnlySocket();

        await socket.dispatch('open', new Event('open'));
        await socket.dispatch('message', createBlobMessageEvent(Uint8Array.of(3, 7, 8, 9)));

        const scenario = await scenarioPromise;
        const walletPromise = scenario.wallet;
        await socket.dispatch('message', createBlobMessageEvent(Uint8Array.of()));
        await socket.dispatch('message', createBlobMessageEvent(HELLO_RSP));
        await walletPromise;

        const requestHandler = getLastProtocolRequestHandler();
        const responsePromise = requestHandler('authorize', { identity: { name: 'Test App' } });
        await flushPromises();

        mockDecryptJsonRpcMessage.mockResolvedValue({
            id: 1,
            jsonrpc: '2.0',
            result: {
                accounts: [],
                auth_token: 'auth-token',
                wallet_uri_base: 'http://wallet.example',
            },
        });
        await socket.dispatch('message', createBlobMessageEvent(INBOUND_SEQUENCE_ONE));

        await expect(responsePromise).rejects.toEqual(
            expect.objectContaining({
                code: SolanaMobileWalletAdapterErrorCode.ERROR_FORBIDDEN_WALLET_BASE_URL,
                name: 'SolanaMobileWalletAdapterError',
            }),
        );
    });

    it('rejects the wallet promise when the remote scenario closes before wallet connection', async () => {
        const scenarioPromise = startRemoteScenario(createRemoteConfig());
        await flushPromises();
        const socket = getOnlySocket();

        await socket.dispatch('open', new Event('open'));
        await socket.dispatch('message', createBlobMessageEvent(Uint8Array.of(3, 7, 8, 9)));

        const scenario = await scenarioPromise;
        const walletPromise = scenario.wallet;
        scenario.close();

        await expect(walletPromise).rejects.toEqual(
            expect.objectContaining({
                code: SolanaMobileWalletAdapterErrorCode.ERROR_SESSION_CLOSED,
                name: 'SolanaMobileWalletAdapterError',
            }),
        );
        expect(socket.close).toHaveBeenCalledTimes(1);
    });
});

describe('startNostrScenario', () => {
    it('establishes a local session through the nostr relay', async () => {
        const scenario = await startNostrScenario(createNostrConfig('local'));
        const socket = getOnlySocket();

        await socket.dispatch('open', new Event('open'));
        await socket.dispatch('message', createNostrRelayEventMessage(''));
        await socket.dispatch('message', createNostrRelayEventMessage(fromUint8Array(HELLO_RSP)));

        await expect(scenario.wallet).resolves.toBe(WALLET);
        const requestHandler = getLastProtocolRequestHandler();
        const responseResult = {
            signed_payloads: [],
        };
        const responsePromise = requestHandler('sign_messages', {
            addresses: [],
            payloads: [],
        });
        await flushPromises();

        expect(mockCreateNostrEvent).toHaveBeenCalledWith(
            20012,
            fromUint8Array(HELLO_REQ),
            expect.any(Array),
            new Uint8Array([1, 2, 3]),
        );

        mockDecryptJsonRpcMessage.mockResolvedValue({
            id: 1,
            jsonrpc: '2.0',
            result: responseResult,
        });
        await socket.dispatch('message', createNostrRelayEventMessage(fromUint8Array(INBOUND_SEQUENCE_ONE)));

        await expect(responsePromise).resolves.toBe(responseResult);
    });

    it('establishes a remote session through the nostr relay', async () => {
        const scenario = (await startNostrScenario(createNostrConfig('remote'))) as RemoteScenario;
        const socket = getOnlySocket();

        expect(scenario.associationUrl).toBe(ASSOCIATION_URL);
        expect(mockStartNostrSession).toHaveBeenCalledWith(
            ASSOCIATION_KEYPAIR.publicKey,
            'remote',
            'relay.nostr.example',
            'dapp-nostr-pubkey',
            'https://wallet.example',
        );

        await socket.dispatch('open', new Event('open'));
        await socket.dispatch('message', createNostrRelayEventMessage(''));
        await socket.dispatch('message', createNostrRelayEventMessage(fromUint8Array(HELLO_RSP)));

        await expect(scenario.wallet).resolves.toBe(WALLET);
        const requestHandler = getLastProtocolRequestHandler();
        const responseResult = {
            signed_payloads: [],
        };
        const responsePromise = requestHandler('sign_messages', {
            addresses: [],
            payloads: [],
        });
        await flushPromises();

        expect(mockCreateNostrEvent).toHaveBeenCalledWith(
            20012,
            fromUint8Array(HELLO_REQ),
            expect.any(Array),
            new Uint8Array([1, 2, 3]),
        );

        mockDecryptJsonRpcMessage.mockResolvedValue({
            id: 1,
            jsonrpc: '2.0',
            result: responseResult,
        });
        await socket.dispatch('message', createNostrRelayEventMessage(fromUint8Array(INBOUND_SEQUENCE_ONE)));

        await expect(responsePromise).resolves.toBe(responseResult);
    });

    it('retries nostr socket connections before the timeout expires', async () => {
        vi.useFakeTimers();
        vi.setSystemTime(0);
        const scenario = await startNostrScenario(createNostrConfig('local'));
        const firstSocket = getOnlySocket();

        const dispatchPromise = firstSocket.dispatch('error', new Event('error'));
        await vi.advanceTimersByTimeAsync(150);
        await dispatchPromise;

        expect(MockWebSocket.instances).toHaveLength(2);
        const secondSocket = MockWebSocket.instances[1];
        await secondSocket.dispatch('open', new Event('open'));
        await secondSocket.dispatch('message', createNostrRelayEventMessage(''));
        await secondSocket.dispatch('message', createNostrRelayEventMessage(fromUint8Array(HELLO_RSP)));

        await expect(scenario.wallet).resolves.toBe(WALLET);
    });

    it('throws when the relay sends a non-empty event while awaiting reflection', async () => {
        const scenario = await startNostrScenario(createNostrConfig('local'));
        const socket = getOnlySocket();

        await socket.dispatch('open', new Event('open'));

        await expect(
            socket.dispatch('message', createNostrRelayEventMessage('unexpected-content')),
        ).resolves.toBeUndefined();
        await expect(scenario.wallet).rejects.toThrow('Encountered unexpected message while awaiting reflection');
    });

    it('throws when the relay sends an invalid message', async () => {
        const scenario = await startNostrScenario(createNostrConfig('local'));
        const socket = getOnlySocket();

        await socket.dispatch('open', new Event('open'));

        await expect(
            socket.dispatch('message', new MessageEvent('message', { data: 'not-json{' })),
        ).resolves.toBeUndefined();
        await expect(scenario.wallet).rejects.toThrow('Invalid Nostr message received');
    });

    it('parses nostr session properties from hello responses', async () => {
        const scenario = await startNostrScenario(createNostrConfig('local'));
        const socket = getOnlySocket();
        const sessionPropertiesBuffer = Uint8Array.of(0, 0, 0, 1, 12);

        await socket.dispatch('open', new Event('open'));
        await socket.dispatch('message', createNostrRelayEventMessage(''));
        await socket.dispatch(
            'message',
            createNostrRelayEventMessage(fromUint8Array(Uint8Array.of(...HELLO_RSP, ...sessionPropertiesBuffer))),
        );

        await expect(scenario.wallet).resolves.toBe(WALLET);
        expect(mockParseSessionProps).toHaveBeenCalledWith(expect.any(ArrayBuffer), SHARED_SECRET);
        expect(new Uint8Array(mockParseSessionProps.mock.calls[0][0] as ArrayBuffer)).toEqual(sessionPropertiesBuffer);
    });

    it('rejects pending relay RPC requests when the wallet returns protocol errors', async () => {
        const scenario = await startNostrScenario(createNostrConfig('local'));
        const socket = getOnlySocket();

        await socket.dispatch('open', new Event('open'));
        await socket.dispatch('message', createNostrRelayEventMessage(''));
        await socket.dispatch('message', createNostrRelayEventMessage(fromUint8Array(HELLO_RSP)));
        await scenario.wallet;

        const protocolError = new SolanaMobileWalletAdapterProtocolError(1, -3, 'not signed');
        const requestHandler = getLastProtocolRequestHandler();
        const responsePromise = requestHandler('sign_messages', {
            addresses: [],
            payloads: [],
        });
        await flushPromises();

        mockDecryptJsonRpcMessage.mockRejectedValue(protocolError);
        await socket.dispatch('message', createNostrRelayEventMessage(fromUint8Array(INBOUND_SEQUENCE_ONE)));

        await expect(responsePromise).rejects.toBe(protocolError);
    });

    it('rejects nostr authorization responses with insecure wallet base URLs', async () => {
        const scenario = await startNostrScenario(createNostrConfig('local'));
        const socket = getOnlySocket();

        await socket.dispatch('open', new Event('open'));
        await socket.dispatch('message', createNostrRelayEventMessage(''));
        await socket.dispatch('message', createNostrRelayEventMessage(fromUint8Array(HELLO_RSP)));
        await scenario.wallet;

        const requestHandler = getLastProtocolRequestHandler();
        const responsePromise = requestHandler('authorize', { identity: { name: 'Test App' } });
        await flushPromises();

        mockDecryptJsonRpcMessage.mockResolvedValue({
            id: 1,
            jsonrpc: '2.0',
            result: {
                accounts: [],
                auth_token: 'auth-token',
                wallet_uri_base: 'http://wallet.example',
            },
        });
        await socket.dispatch('message', createNostrRelayEventMessage(fromUint8Array(INBOUND_SEQUENCE_ONE)));

        await expect(responsePromise).rejects.toEqual(
            expect.objectContaining({
                code: SolanaMobileWalletAdapterErrorCode.ERROR_FORBIDDEN_WALLET_BASE_URL,
                name: 'SolanaMobileWalletAdapterError',
            }),
        );
    });

    it('rejects the wallet promise when the nostr scenario closes before wallet connection', async () => {
        const scenario = await startNostrScenario(createNostrConfig('local'));
        const socket = getOnlySocket();

        scenario.close();

        await expect(scenario.wallet).rejects.toEqual(
            expect.objectContaining({
                code: SolanaMobileWalletAdapterErrorCode.ERROR_SESSION_CLOSED,
                name: 'SolanaMobileWalletAdapterError',
            }),
        );
        expect(socket.close).toHaveBeenCalledTimes(1);
    });
});

async function establishLocalSession(socket: MockWebSocket) {
    await socket.dispatch('open', new Event('open'));
    await socket.dispatch('message', createBlobMessageEvent(HELLO_RSP));
}

async function flushPromises() {
    await Promise.resolve();
    await Promise.resolve();
}

function createBlobMessageEvent(bytes: Uint8Array) {
    return new MessageEvent('message', {
        data: new Blob([bytes as BlobPart]),
    });
}

function createRemoteConfig(): RemoteWalletAssociationConfig {
    return {
        baseUri: 'https://wallet.example',
        remoteHostAuthority: 'reflector.example',
    };
}

function createNostrConfig(connectionType: 'local' | 'remote'): NostrWalletAssociationConfig {
    return {
        baseUri: 'https://wallet.example',
        relayDomain: 'relay.nostr.example',
        connectionType: connectionType,
    };
}

function createStringMessageEvent(data: string) {
    return new MessageEvent('message', {
        data,
    });
}

function createNostrRelayEventMessage(content: string, pubkey = 'wallet-nostr-pubkey') {
    return new MessageEvent('message', {
        data: JSON.stringify([
            'EVENT',
            'mock-sub-id',
            {
                id: 'mock-wallet-event-id',
                pubkey,
                created_at: 1000,
                kind: 20012,
                tags: [],
                content,
                sig: 'mock-wallet-sig',
            },
        ]),
    });
}

function getLastProtocolRequestHandler() {
    const lastCall = mockCreateMobileWalletProxy.mock.lastCall as
        | [protocolVersion: string, requestHandler: (method: string, params?: unknown) => Promise<unknown>]
        | undefined;
    expect(lastCall).toBeDefined();
    return lastCall![1];
}

function getOnlySocket() {
    expect(MockWebSocket.instances).toHaveLength(1);
    return MockWebSocket.instances[0];
}

type WebSocketListener = (event: Event | MessageEvent) => unknown;

class MockWebSocket {
    static instances: MockWebSocket[] = [];

    readonly close = vi.fn();
    readonly listeners = new Map<string, Set<WebSocketListener>>();
    readonly sent: unknown[] = [];
    protocol = 'com.solana.mobilewalletadapter.v1';

    constructor(
        readonly url: string,
        readonly protocols: string[],
    ) {
        MockWebSocket.instances.push(this);
    }

    addEventListener(eventName: string, listener: WebSocketListener) {
        const listeners = this.listeners.get(eventName) ?? new Set<WebSocketListener>();
        listeners.add(listener);
        this.listeners.set(eventName, listeners);
    }

    async dispatch(eventName: string, event: Event | MessageEvent) {
        await Promise.all([...(this.listeners.get(eventName) ?? [])].map((listener) => listener(event)));
    }

    removeEventListener(eventName: string, listener: WebSocketListener) {
        this.listeners.get(eventName)?.delete(listener);
    }

    send(message: unknown) {
        this.sent.push(message);
    }
}
