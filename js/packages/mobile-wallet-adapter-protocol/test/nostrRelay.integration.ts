import { GenericContainer, StartedTestContainer, Wait } from 'testcontainers';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';

import {
    createNostrEvent,
    generateNostrKeypair,
    NOSTR_EVENT_KIND_MWA,
    type NostrEvent,
    verifyNostrEvent,
} from '../src/nostr.js';

const RELAY_IMAGE = 'scsibug/nostr-rs-relay:0.9.0';
const RELAY_PORT = 8080;
const RELAY_TIMEOUT_MS = 5000;

let relayContainer: StartedTestContainer | undefined;
let relayUrl: string;

beforeAll(async () => {
    relayContainer = await new GenericContainer(RELAY_IMAGE)
        .withExposedPorts(RELAY_PORT)
        .withWaitStrategy(Wait.forListeningPorts())
        .start();
    relayUrl = `ws://${relayContainer.getHost()}:${relayContainer.getMappedPort(RELAY_PORT)}`;
});

afterAll(async () => {
    await relayContainer?.stop();
});

describe('Nostr relay integration', () => {
    it('relays MWA QR session events between dapp and wallet clients', async () => {
        const dapp = generateNostrKeypair();
        const dappSubscriptionId = `dapp-${crypto.randomUUID()}`;
        const helloContent = 'hello-from-dapp';
        const sessionIdentifier = crypto.randomUUID();
        const wallet = generateNostrKeypair();
        const walletSubscriptionId = `wallet-${crypto.randomUUID()}`;

        const dappSocket = await connectWebSocket(relayUrl);
        const walletSocket = await connectWebSocket(relayUrl);

        try {
            const dappEventPromise = waitForNostrEvent(
                dappSocket,
                dappSubscriptionId,
                (event) => event.content === '' && event.pubkey === wallet.publicKey,
            );
            const walletEventPromise = waitForNostrEvent(
                walletSocket,
                walletSubscriptionId,
                (event) => event.content === helloContent && event.pubkey === dapp.publicKey,
            );

            dappSocket.send(
                JSON.stringify([
                    'REQ',
                    dappSubscriptionId,
                    { '#d': [sessionIdentifier], kinds: [NOSTR_EVENT_KIND_MWA] },
                ]),
            );
            walletSocket.send(
                JSON.stringify([
                    'REQ',
                    walletSubscriptionId,
                    { '#d': [sessionIdentifier], kinds: [NOSTR_EVENT_KIND_MWA] },
                ]),
            );
            await Promise.all([
                waitForRelayMessage(
                    dappSocket,
                    (message) => message[0] === 'EOSE' && message[1] === dappSubscriptionId,
                ),
                waitForRelayMessage(
                    walletSocket,
                    (message) => message[0] === 'EOSE' && message[1] === walletSubscriptionId,
                ),
            ]);

            const walletReflection = createNostrEvent(
                NOSTR_EVENT_KIND_MWA,
                '',
                [
                    ['d', sessionIdentifier],
                    ['p', dapp.publicKey],
                ],
                wallet.privateKey,
            );
            const walletOkPromise = waitForRelayMessage(
                walletSocket,
                (message) => message[0] === 'OK' && message[1] === walletReflection.id,
            );
            walletSocket.send(JSON.stringify(['EVENT', walletReflection]));

            const walletOk = await walletOkPromise;
            expect(walletOk[2]).toBe(true);

            const receivedReflection = await dappEventPromise;
            expect(receivedReflection).toEqual(walletReflection);
            expect(verifyNostrEvent(receivedReflection)).toBe(true);

            const dappHello = createNostrEvent(
                NOSTR_EVENT_KIND_MWA,
                helloContent,
                [
                    ['d', sessionIdentifier],
                    ['p', wallet.publicKey],
                ],
                dapp.privateKey,
            );
            const dappOkPromise = waitForRelayMessage(
                dappSocket,
                (message) => message[0] === 'OK' && message[1] === dappHello.id,
            );
            dappSocket.send(JSON.stringify(['EVENT', dappHello]));

            const dappOk = await dappOkPromise;
            expect(dappOk[2]).toBe(true);

            const receivedHello = await walletEventPromise;
            expect(receivedHello).toEqual(dappHello);
            expect(verifyNostrEvent(receivedHello)).toBe(true);
        } finally {
            closeWebSocket(dappSocket);
            closeWebSocket(walletSocket);
        }
    });
});

function closeWebSocket(socket: WebSocket) {
    if (socket.readyState === WebSocket.CLOSED || socket.readyState === WebSocket.CLOSING) {
        return;
    }
    socket.close();
}

function connectWebSocket(url: string): Promise<WebSocket> {
    return new Promise((resolve, reject) => {
        const socket = new WebSocket(url);
        const timeoutId = setTimeout(() => {
            cleanup();
            closeWebSocket(socket);
            reject(new Error(`Timed out connecting to ${url}`));
        }, RELAY_TIMEOUT_MS);

        function cleanup() {
            clearTimeout(timeoutId);
            socket.removeEventListener('error', handleError);
            socket.removeEventListener('open', handleOpen);
        }

        function handleError() {
            cleanup();
            reject(new Error(`Failed to connect to ${url}`));
        }

        function handleOpen() {
            cleanup();
            resolve(socket);
        }

        socket.addEventListener('error', handleError, { once: true });
        socket.addEventListener('open', handleOpen, { once: true });
    });
}

async function getMessageText(event: MessageEvent): Promise<string> {
    if (event.data instanceof ArrayBuffer) {
        return new TextDecoder().decode(event.data);
    }
    if (event.data instanceof Blob) {
        return event.data.text();
    }
    if (ArrayBuffer.isView(event.data)) {
        return new TextDecoder().decode(event.data);
    }
    return event.data;
}

function waitForNostrEvent(
    socket: WebSocket,
    subscriptionId: string,
    predicate: (event: NostrEvent) => boolean,
): Promise<NostrEvent> {
    return waitForRelayMessage(
        socket,
        (message) => {
            if (message[0] !== 'EVENT' || message[1] !== subscriptionId || typeof message[2] !== 'object') {
                return false;
            }
            return predicate(message[2] as NostrEvent);
        },
        `Nostr EVENT on ${subscriptionId}`,
    ).then((message) => message[2] as NostrEvent);
}

function waitForRelayMessage(
    socket: WebSocket,
    predicate: (message: unknown[]) => boolean,
    description = 'relay message',
): Promise<unknown[]> {
    return new Promise((resolve, reject) => {
        const timeoutId = setTimeout(() => {
            cleanup();
            reject(new Error(`Timed out waiting for ${description}`));
        }, RELAY_TIMEOUT_MS);

        function cleanup() {
            clearTimeout(timeoutId);
            socket.removeEventListener('close', handleClose);
            socket.removeEventListener('error', handleError);
            socket.removeEventListener('message', handleMessage);
        }

        function handleClose() {
            cleanup();
            reject(new Error(`WebSocket closed while waiting for ${description}`));
        }

        function handleError() {
            cleanup();
            reject(new Error(`WebSocket error while waiting for ${description}`));
        }

        async function handleMessage(event: MessageEvent) {
            const message = JSON.parse(await getMessageText(event));
            if (!Array.isArray(message) || !predicate(message)) {
                return;
            }
            cleanup();
            resolve(message);
        }

        socket.addEventListener('close', handleClose, { once: true });
        socket.addEventListener('error', handleError, { once: true });
        socket.addEventListener('message', handleMessage);
    });
}
