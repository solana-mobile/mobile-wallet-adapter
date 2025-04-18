import { fromUint8Array, toUint8Array } from './base64Utils.js';
import createHelloReq from './createHelloReq.js';
import createMobileWalletProxy from './createMobileWalletProxy.js';
import { SEQUENCE_NUMBER_BYTES } from './createSequenceNumberVector.js';
import { ENCODED_PUBLIC_KEY_LENGTH_BYTES } from './encryptedMessage.js';
import {
    SolanaMobileWalletAdapterError,
    SolanaMobileWalletAdapterErrorCode,
    SolanaMobileWalletAdapterProtocolError,
} from './errors.js';
import generateAssociationKeypair from './generateAssociationKeypair.js';
import generateECDHKeypair from './generateECDHKeypair.js';
import { getRemoteAssociateAndroidIntentURL } from './getAssociateAndroidIntentURL.js';
import { decryptJsonRpcMessage, encryptJsonRpcMessage } from './jsonRpcMessage.js';
import parseHelloRsp, { SharedSecret } from './parseHelloRsp.js';
import parseSessionProps from './parseSessionProps.js';
import { startSession } from './startSession.js';
import { 
    AssociationKeypair, 
    MobileWallet, 
    RemoteMobileWallet, 
    RemoteScenario, 
    RemoteWalletAssociationConfig, 
    SessionProperties, 
    WalletAssociationConfig 
} from './types.js';

const WEBSOCKET_CONNECTION_CONFIG = {
    /**
     * 300 milliseconds is a generally accepted threshold for what someone
     * would consider an acceptable response time for a user interface
     * after having performed a low-attention tapping task. We set the initial
     * interval at which we wait for the wallet to set up the websocket at
     * half this, as per the Nyquist frequency, with a progressive backoff
     * sequence from there. The total wait time is 30s, which allows for the
     * user to be presented with a disambiguation dialog, select a wallet, and
     * for the wallet app to subsequently start.
     */
    retryDelayScheduleMs: [150, 150, 200, 500, 500, 750, 750, 1000],
    timeoutMs: 30000,
} as const;
const WEBSOCKET_PROTOCOL_BINARY = 'com.solana.mobilewalletadapter.v1';
const WEBSOCKET_PROTOCOL_BASE64 = 'com.solana.mobilewalletadapter.v1.base64';
type PROTOCOL_ENCODING = 'binary' | 'base64';

type JsonResponsePromises<T> = Record<
    number,
    Readonly<{ resolve: (value?: T | PromiseLike<T>) => void; reject: (reason?: unknown) => void }>
>;

type State =
    | { __type: 'connected'; sharedSecret: SharedSecret; sessionProperties: SessionProperties }
    | { __type: 'connecting'; associationKeypair: AssociationKeypair }
    | { __type: 'disconnected' }
    | { __type: 'hello_req_sent'; associationPublicKey: CryptoKey; ecdhPrivateKey: CryptoKey };

type RemoteState = State | { __type: 'reflector_id_received'; reflectorId: ArrayBuffer }; 

function assertSecureContext() {
    if (typeof window === 'undefined' || window.isSecureContext !== true) {
        throw new SolanaMobileWalletAdapterError(
            SolanaMobileWalletAdapterErrorCode.ERROR_SECURE_CONTEXT_REQUIRED,
            'The mobile wallet adapter protocol must be used in a secure context (`https`).',
        );
    }
}

function assertSecureEndpointSpecificURI(walletUriBase: string) {
    let url: URL;
    try {
        url = new URL(walletUriBase);
    } catch {
        throw new SolanaMobileWalletAdapterError(
            SolanaMobileWalletAdapterErrorCode.ERROR_FORBIDDEN_WALLET_BASE_URL,
            'Invalid base URL supplied by wallet',
        );
    }
    if (url.protocol !== 'https:') {
        throw new SolanaMobileWalletAdapterError(
            SolanaMobileWalletAdapterErrorCode.ERROR_FORBIDDEN_WALLET_BASE_URL,
            'Base URLs supplied by wallets must be valid `https` URLs',
        );
    }
}

function getSequenceNumberFromByteArray(byteArray: ArrayBuffer): number {
    const view = new DataView(byteArray);
    return view.getUint32(0, /* littleEndian */ false);
}

function decodeVarLong(byteArray: ArrayBuffer): { value: number, offset: number} {
    var bytes = new Uint8Array(byteArray), 
        l = byteArray.byteLength,
        limit = 10,
        value = 0, 
        offset = 0, 
        b;
    do {
        if (offset >= l || offset > limit) throw new RangeError('Failed to decode varint');
        b = bytes[offset++];
        value |= (b & 0x7F) << (7 * offset);
    } while (b >= 0x80);

    return { value, offset };
}

function getReflectorIdFromByteArray(byteArray: ArrayBuffer): Uint8Array {
    let { value: length, offset } = decodeVarLong(byteArray);
    return new Uint8Array(byteArray.slice(offset, offset + length));
}

export async function transact<TReturn>(
    callback: (wallet: MobileWallet) => TReturn,
    config?: WalletAssociationConfig,
): Promise<TReturn> {
    assertSecureContext();
    const associationKeypair = await generateAssociationKeypair();
    const sessionPort = await startSession(associationKeypair.publicKey, config?.baseUri);
    const websocketURL = `ws://localhost:${sessionPort}/solana-wallet`;
    let connectionStartTime: number;
    const getNextRetryDelayMs = (() => {
        const schedule = [...WEBSOCKET_CONNECTION_CONFIG.retryDelayScheduleMs];
        return () => (schedule.length > 1 ? (schedule.shift() as number) : schedule[0]);
    })();
    let nextJsonRpcMessageId = 1;
    let lastKnownInboundSequenceNumber = 0;
    let state: State = { __type: 'disconnected' };
    return new Promise((resolve, reject) => {
        let socket: WebSocket;
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const jsonRpcResponsePromises: JsonResponsePromises<any> = {};
        const handleOpen = async () => {
            if (state.__type !== 'connecting') {
                console.warn(
                    'Expected adapter state to be `connecting` at the moment the websocket opens. ' +
                        `Got \`${state.__type}\`.`,
                );
                return;
            }
            socket.removeEventListener('open', handleOpen);

            // previous versions of this library and walletlib incorrectly implemented the MWA session 
            // establishment protocol for local connections. The dapp is supposed to wait for the 
            // APP_PING message before sending the HELLO_REQ. Instead, the dapp was sending the HELLO_REQ 
            // immediately upon connection to the websocket server regardless of wether or not an 
            // APP_PING was sent by the wallet/websocket server. We must continue to support this behavior 
            // in case the user is using a wallet that has not updated their walletlib implementation. 
            const { associationKeypair } = state;
            const ecdhKeypair = await generateECDHKeypair();
            socket.send(await createHelloReq(ecdhKeypair.publicKey, associationKeypair.privateKey));
            state = {
                __type: 'hello_req_sent',
                associationPublicKey: associationKeypair.publicKey,
                ecdhPrivateKey: ecdhKeypair.privateKey,
            };
        };
        const handleClose = (evt: CloseEvent) => {
            if (evt.wasClean) {
                state = { __type: 'disconnected' };
            } else {
                reject(
                    new SolanaMobileWalletAdapterError(
                        SolanaMobileWalletAdapterErrorCode.ERROR_SESSION_CLOSED,
                        `The wallet session dropped unexpectedly (${evt.code}: ${evt.reason}).`,
                        { closeEvent: evt },
                    ),
                );
            }
            disposeSocket();
        };
        const handleError = async (_evt: Event) => {
            disposeSocket();
            if (Date.now() - connectionStartTime >= WEBSOCKET_CONNECTION_CONFIG.timeoutMs) {
                reject(
                    new SolanaMobileWalletAdapterError(
                        SolanaMobileWalletAdapterErrorCode.ERROR_SESSION_TIMEOUT,
                        `Failed to connect to the wallet websocket at ${websocketURL}.`,
                    ),
                );
            } else {
                await new Promise((resolve) => {
                    const retryDelayMs = getNextRetryDelayMs();
                    retryWaitTimeoutId = window.setTimeout(resolve, retryDelayMs);
                });
                attemptSocketConnection();
            }
        };
        const handleMessage = async (evt: MessageEvent<Blob>) => {
            const responseBuffer = await evt.data.arrayBuffer();
            switch (state.__type) {
                case 'connecting':
                    if (responseBuffer.byteLength !== 0) {
                        throw new Error('Encountered unexpected message while connecting');
                    }
                    const ecdhKeypair = await generateECDHKeypair();
                    socket.send(await createHelloReq(ecdhKeypair.publicKey, associationKeypair.privateKey));
                    state = {
                        __type: 'hello_req_sent',
                        associationPublicKey: associationKeypair.publicKey,
                        ecdhPrivateKey: ecdhKeypair.privateKey,
                    };
                    break;
                case 'connected':
                    try {
                        const sequenceNumberVector = responseBuffer.slice(0, SEQUENCE_NUMBER_BYTES);
                        const sequenceNumber = getSequenceNumberFromByteArray(sequenceNumberVector);
                        if (sequenceNumber !== (lastKnownInboundSequenceNumber + 1)) {
                            throw new Error('Encrypted message has invalid sequence number');
                        }
                        lastKnownInboundSequenceNumber = sequenceNumber;
                        const jsonRpcMessage = await decryptJsonRpcMessage(responseBuffer, state.sharedSecret);
                        const responsePromise = jsonRpcResponsePromises[jsonRpcMessage.id];
                        delete jsonRpcResponsePromises[jsonRpcMessage.id];
                        responsePromise.resolve(jsonRpcMessage.result);
                    } catch (e) {
                        if (e instanceof SolanaMobileWalletAdapterProtocolError) {
                            const responsePromise = jsonRpcResponsePromises[e.jsonRpcMessageId];
                            delete jsonRpcResponsePromises[e.jsonRpcMessageId];
                            responsePromise.reject(e);
                        } else {
                            throw e;
                        }
                    }
                    break;
                case 'hello_req_sent': {
                    // if we receive an APP_PING message (empty message), resend the HELLO_REQ (see above)
                    if (responseBuffer.byteLength === 0) {
                        const ecdhKeypair = await generateECDHKeypair();
                        socket.send(await createHelloReq(ecdhKeypair.publicKey, associationKeypair.privateKey));
                        state = {
                            __type: 'hello_req_sent',
                            associationPublicKey: associationKeypair.publicKey,
                            ecdhPrivateKey: ecdhKeypair.privateKey,
                        };
                        break;
                    }
                    const sharedSecret = await parseHelloRsp(
                        responseBuffer,
                        state.associationPublicKey,
                        state.ecdhPrivateKey,
                    );
                    const sessionPropertiesBuffer = responseBuffer.slice(ENCODED_PUBLIC_KEY_LENGTH_BYTES);
                    const sessionProperties = sessionPropertiesBuffer.byteLength !== 0 
                        ? await (async () => {
                            const sequenceNumberVector = sessionPropertiesBuffer.slice(0, SEQUENCE_NUMBER_BYTES);
                            const sequenceNumber = getSequenceNumberFromByteArray(sequenceNumberVector);
                            if (sequenceNumber !== (lastKnownInboundSequenceNumber + 1)) {
                                throw new Error('Encrypted message has invalid sequence number');
                            }
                            lastKnownInboundSequenceNumber = sequenceNumber;
                            return parseSessionProps(sessionPropertiesBuffer, sharedSecret);
                        })() : <SessionProperties> { protocol_version: 'legacy' };
                    state = { __type: 'connected', sharedSecret, sessionProperties };
                    const wallet = createMobileWalletProxy(sessionProperties.protocol_version,
                        async (method, params) => {
                            const id = nextJsonRpcMessageId++;
                            socket.send(
                                await encryptJsonRpcMessage(
                                    {
                                        id,
                                        jsonrpc: '2.0' as const,
                                        method,
                                        params: params ?? {},
                                    }, 
                                    sharedSecret,
                                ),
                            );
                            return new Promise((resolve, reject) => {
                                jsonRpcResponsePromises[id] = {
                                    resolve(result) {
                                        switch (method) {
                                            case 'authorize':
                                            case 'reauthorize': {
                                                const { wallet_uri_base } = result as Awaited<
                                                    ReturnType<MobileWallet['authorize' | 'reauthorize']>
                                                >;
                                                if (wallet_uri_base != null) {
                                                    try {
                                                        assertSecureEndpointSpecificURI(wallet_uri_base);
                                                    } catch (e) {
                                                        reject(e);
                                                        return;
                                                    }
                                                }
                                                break;
                                            }
                                        }
                                        resolve(result);
                                    },
                                    reject,
                                };
                            });
                        })
                    try {
                        resolve(await callback(wallet));
                    } catch (e) {
                        reject(e);
                    } finally {
                        disposeSocket();
                        socket.close();
                    }
                    break;
                }
            }
        };
        let disposeSocket: () => void;
        let retryWaitTimeoutId: number;
        const attemptSocketConnection = () => {
            if (disposeSocket) {
                disposeSocket();
            }
            state = { __type: 'connecting', associationKeypair };
            if (connectionStartTime === undefined) {
                connectionStartTime = Date.now();
            }
            socket = new WebSocket(websocketURL, [WEBSOCKET_PROTOCOL_BINARY]);
            socket.addEventListener('open', handleOpen);
            socket.addEventListener('close', handleClose);
            socket.addEventListener('error', handleError);
            socket.addEventListener('message', handleMessage);
            disposeSocket = () => {
                window.clearTimeout(retryWaitTimeoutId);
                socket.removeEventListener('open', handleOpen);
                socket.removeEventListener('close', handleClose);
                socket.removeEventListener('error', handleError);
                socket.removeEventListener('message', handleMessage);
            };
        };
        attemptSocketConnection();
    });
}

export async function startRemoteScenario(
    config: RemoteWalletAssociationConfig,
): Promise<RemoteScenario> {
    assertSecureContext();
    const associationKeypair = await generateAssociationKeypair();
    const websocketURL = `wss://${config?.remoteHostAuthority}/reflect`;
    let connectionStartTime: number;
    const getNextRetryDelayMs = (() => {
        const schedule = [...WEBSOCKET_CONNECTION_CONFIG.retryDelayScheduleMs];
        return () => (schedule.length > 1 ? (schedule.shift() as number) : schedule[0]);
    })();
    let nextJsonRpcMessageId = 1;
    let lastKnownInboundSequenceNumber = 0;
    let encoding: PROTOCOL_ENCODING;
    let state: RemoteState = { __type: 'disconnected' };
    let socket: WebSocket;
    let disposeSocket: () => void;
    let decodeBytes = async (evt: MessageEvent<string | Blob>) => {
        if (encoding == 'base64') { // base64 encoding
            const message = await evt.data as string;
            return toUint8Array(message).buffer;
        } else {
            return await (evt.data as Blob).arrayBuffer();
        }
    };
    // Reflector Connection Phase
    // here we connect to the reflector and wait for the REFLECTOR_ID message 
    // so we build the association URL and return that back to the caller
    const associationUrl = await new Promise<URL>((resolve, reject) => {
        const handleOpen = async () => {
            if (state.__type !== 'connecting') {
                console.warn(
                    'Expected adapter state to be `connecting` at the moment the websocket opens. ' +
                        `Got \`${state.__type}\`.`,
                );
                return;
            }
            if (socket.protocol.includes(WEBSOCKET_PROTOCOL_BASE64)) {
                encoding = 'base64';
            } else {
                encoding = 'binary';
            }
            socket.removeEventListener('open', handleOpen);
        };
        const handleClose = (evt: CloseEvent) => {
            if (evt.wasClean) {
                state = { __type: 'disconnected' };
            } else {
                reject(
                    new SolanaMobileWalletAdapterError(
                        SolanaMobileWalletAdapterErrorCode.ERROR_SESSION_CLOSED,
                        `The wallet session dropped unexpectedly (${evt.code}: ${evt.reason}).`,
                        { closeEvent: evt },
                    ),
                );
            }
            disposeSocket();
        };
        const handleError = async (_evt: Event) => {
            disposeSocket();
            if (Date.now() - connectionStartTime >= WEBSOCKET_CONNECTION_CONFIG.timeoutMs) {
                reject(
                    new SolanaMobileWalletAdapterError(
                        SolanaMobileWalletAdapterErrorCode.ERROR_SESSION_TIMEOUT,
                        `Failed to connect to the wallet websocket at ${websocketURL}.`,
                    ),
                );
            } else {
                await new Promise((resolve) => {
                    const retryDelayMs = getNextRetryDelayMs();
                    retryWaitTimeoutId = window.setTimeout(resolve, retryDelayMs);
                });
                attemptSocketConnection();
            }
        };
        const handleReflectorIdMessage = async (evt: MessageEvent<string | Blob>) => {
            const responseBuffer = await decodeBytes(evt);
            if (state.__type === 'connecting') {
                if (responseBuffer.byteLength == 0) {
                    throw new Error('Encountered unexpected message while connecting');
                }
                const reflectorId = getReflectorIdFromByteArray(responseBuffer);
                state = {
                    __type: 'reflector_id_received',
                    reflectorId: reflectorId
                };
                const associationUrl = await getRemoteAssociateAndroidIntentURL(
                    associationKeypair.publicKey, 
                    config.remoteHostAuthority, 
                    reflectorId,
                    config?.baseUri
                );
                socket.removeEventListener('message', handleReflectorIdMessage);
                resolve(associationUrl);
            }
        };
        let retryWaitTimeoutId: number;
        const attemptSocketConnection = () => {
            if (disposeSocket) {
                disposeSocket();
            }
            state = { __type: 'connecting', associationKeypair };
            if (connectionStartTime === undefined) {
                connectionStartTime = Date.now();
            }
            socket = new WebSocket(websocketURL, 
                [WEBSOCKET_PROTOCOL_BINARY, WEBSOCKET_PROTOCOL_BASE64]);
            socket.addEventListener('open', handleOpen);
            socket.addEventListener('close', handleClose);
            socket.addEventListener('error', handleError);
            socket.addEventListener('message', handleReflectorIdMessage);
            disposeSocket = () => {
                window.clearTimeout(retryWaitTimeoutId);
                socket.removeEventListener('open', handleOpen);
                socket.removeEventListener('close', handleClose);
                socket.removeEventListener('error', handleError);
                socket.removeEventListener('message', handleReflectorIdMessage);
            };
        };
        attemptSocketConnection();
    });
    // Wallet Connection Phase
    // here we return the association URL (containing the reflector ID) to the caller + 
    // a promise that will resolve the MobileWallet object once the wallet connects.
    let sessionEstablished = false;
    let handleClose: () => void;
    return { associationUrl, close: () => {
        socket.close();
        handleClose();
    }, wallet: new Promise((resolve, reject) => {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const jsonRpcResponsePromises: JsonResponsePromises<any> = {};
        const handleMessage = async (evt: MessageEvent<string | Blob>) => {
            const responseBuffer = await decodeBytes(evt);
            switch (state.__type) {
                case 'reflector_id_received':
                    if (responseBuffer.byteLength !== 0) {
                        throw new Error('Encountered unexpected message while awaiting reflection');
                    }
                    const ecdhKeypair = await generateECDHKeypair();
                    const binaryMsg = await createHelloReq(ecdhKeypair.publicKey, associationKeypair.privateKey);
                    if (encoding == 'base64') {
                        socket.send(fromUint8Array(binaryMsg));
                    } else {
                        socket.send(binaryMsg);
                    }
                    state = {
                        __type: 'hello_req_sent',
                        associationPublicKey: associationKeypair.publicKey,
                        ecdhPrivateKey: ecdhKeypair.privateKey,
                    };
                    break;
                case 'connected':
                    try {
                        const sequenceNumberVector = responseBuffer.slice(0, SEQUENCE_NUMBER_BYTES);
                        const sequenceNumber = getSequenceNumberFromByteArray(sequenceNumberVector);
                        if (sequenceNumber !== (lastKnownInboundSequenceNumber + 1)) {
                            throw new Error('Encrypted message has invalid sequence number');
                        }
                        lastKnownInboundSequenceNumber = sequenceNumber;
                        const jsonRpcMessage = await decryptJsonRpcMessage(responseBuffer, state.sharedSecret);
                        const responsePromise = jsonRpcResponsePromises[jsonRpcMessage.id];
                        delete jsonRpcResponsePromises[jsonRpcMessage.id];
                        responsePromise.resolve(jsonRpcMessage.result);
                    } catch (e) {
                        if (e instanceof SolanaMobileWalletAdapterProtocolError) {
                            const responsePromise = jsonRpcResponsePromises[e.jsonRpcMessageId];
                            delete jsonRpcResponsePromises[e.jsonRpcMessageId];
                            responsePromise.reject(e);
                        } else {
                            throw e;
                        }
                    }
                    break;
                case 'hello_req_sent': {
                    const sharedSecret = await parseHelloRsp(
                        responseBuffer,
                        state.associationPublicKey,
                        state.ecdhPrivateKey,
                    );
                    const sessionPropertiesBuffer = responseBuffer.slice(ENCODED_PUBLIC_KEY_LENGTH_BYTES);
                    const sessionProperties = sessionPropertiesBuffer.byteLength !== 0 
                        ? await (async () => {
                            const sequenceNumberVector = sessionPropertiesBuffer.slice(0, SEQUENCE_NUMBER_BYTES);
                            const sequenceNumber = getSequenceNumberFromByteArray(sequenceNumberVector);
                            if (sequenceNumber !== (lastKnownInboundSequenceNumber + 1)) {
                                throw new Error('Encrypted message has invalid sequence number');
                            }
                            lastKnownInboundSequenceNumber = sequenceNumber;
                            return parseSessionProps(sessionPropertiesBuffer, sharedSecret);
                        })() : <SessionProperties> { protocol_version: 'legacy' };
                    state = { __type: 'connected', sharedSecret, sessionProperties };
                    const wallet = createMobileWalletProxy(sessionProperties.protocol_version,
                        async (method, params) => {
                            const id = nextJsonRpcMessageId++;
                            const binaryMsg = await encryptJsonRpcMessage(
                                {
                                    id,
                                    jsonrpc: '2.0' as const,
                                    method,
                                    params: params ?? {},
                                }, 
                                sharedSecret,
                            )
                            if (encoding == 'base64') {
                                socket.send(fromUint8Array(binaryMsg));
                            } else {
                                socket.send(binaryMsg);
                            }
                            return new Promise((resolve, reject) => {
                                jsonRpcResponsePromises[id] = {
                                    resolve(result) {
                                        switch (method) {
                                            case 'authorize':
                                            case 'reauthorize': {
                                                const { wallet_uri_base } = result as Awaited<
                                                    ReturnType<MobileWallet['authorize' | 'reauthorize']>
                                                >;
                                                if (wallet_uri_base != null) {
                                                    try {
                                                        assertSecureEndpointSpecificURI(wallet_uri_base);
                                                    } catch (e) {
                                                        reject(e);
                                                        return;
                                                    }
                                                }
                                                break;
                                            }
                                        }
                                        resolve(result);
                                    },
                                    reject,
                                };
                            });
                        })
                    sessionEstablished = true;
                    try {
                        resolve(wallet);
                    } catch (e) {
                        reject(e);
                    }
                    break;
                }
            }
        }
        socket.addEventListener('message', handleMessage);
        handleClose = () => {
            socket.removeEventListener('message', handleMessage);
            disposeSocket();
            if (!sessionEstablished) {
                reject(
                    new SolanaMobileWalletAdapterError(
                        SolanaMobileWalletAdapterErrorCode.ERROR_SESSION_CLOSED,
                        `The wallet session was closed before connection.`,
                        { closeEvent: new CloseEvent('socket was closed before connection') },
                    ),
                );
            }
        };
    })};
}