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
    mockDecryptJsonRpcMessage,
    mockEncryptJsonRpcMessage,
    mockGenerateAssociationKeypair,
    mockGenerateECDHKeypair,
    mockGetRemoteAssociateAndroidIntentURL,
    mockParseHelloRsp,
    mockParseSessionProps,
    mockStartSession,
} = vi.hoisted(() => ({
    mockCreateHelloReq: vi.fn(),
    mockCreateMobileWalletProxy: vi.fn(),
    mockDecryptJsonRpcMessage: vi.fn(),
    mockEncryptJsonRpcMessage: vi.fn(),
    mockGenerateAssociationKeypair: vi.fn(),
    mockGenerateECDHKeypair: vi.fn(),
    mockGetRemoteAssociateAndroidIntentURL: vi.fn(),
    mockParseHelloRsp: vi.fn(),
    mockParseSessionProps: vi.fn(),
    mockStartSession: vi.fn(),
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
    getRemoteAssociateAndroidIntentURL: mockGetRemoteAssociateAndroidIntentURL,
}));

vi.mock('../src/jsonRpcMessage.js', () => ({
    decryptJsonRpcMessage: mockDecryptJsonRpcMessage,
    encryptJsonRpcMessage: mockEncryptJsonRpcMessage,
}));

vi.mock('../src/parseHelloRsp.js', () => ({
    default: mockParseHelloRsp,
}));

vi.mock('../src/parseSessionProps.js', () => ({
    default: mockParseSessionProps,
}));

vi.mock('../src/startSession.js', () => ({
    startSession: mockStartSession,
}));

import { SolanaMobileWalletAdapterErrorCode, SolanaMobileWalletAdapterProtocolError } from '../src/errors.js';
import { startRemoteScenario, startScenario, transact } from '../src/transact.js';
import { RemoteWalletAssociationConfig } from '../src/types.js';

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
        data: new Blob([bytes]),
    });
}

function createRemoteConfig(): RemoteWalletAssociationConfig {
    return {
        baseUri: 'https://wallet.example',
        remoteHostAuthority: 'reflector.example',
    };
}

function createStringMessageEvent(data: string) {
    return new MessageEvent('message', {
        data,
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
