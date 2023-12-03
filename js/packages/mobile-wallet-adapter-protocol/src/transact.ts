import { SolanaSignTransaction } from '@solana/wallet-standard';
import createHelloReq from './createHelloReq.js';
import { SEQUENCE_NUMBER_BYTES } from './createSequenceNumberVector.js';
import { ENCODED_PUBLIC_KEY_LENGTH_BYTES } from './encryptedMessage.js';
import {
    SolanaMobileWalletAdapterError,
    SolanaMobileWalletAdapterErrorCode,
    SolanaMobileWalletAdapterProtocolError,
} from './errors.js';
import generateAssociationKeypair from './generateAssociationKeypair.js';
import generateECDHKeypair from './generateECDHKeypair.js';
import { decryptJsonRpcMessage, encryptJsonRpcMessage } from './jsonRpcMessage.js';
import parseHelloRsp, { SharedSecret } from './parseHelloRsp.js';
import parseSessionProps from './parseSessionProps.js';
import { startSession } from './startSession.js';
import { AssociationKeypair, MobileWallet, SessionProperties, SolanaCloneAuthorization, WalletAssociationConfig } from './types.js';

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
const WEBSOCKET_PROTOCOL = 'com.solana.mobilewalletadapter.v1';

type JsonResponsePromises<T> = Record<
    number,
    Readonly<{ resolve: (value?: T | PromiseLike<T>) => void; reject: (reason?: unknown) => void }>
>;

type State =
    | { __type: 'connected'; sharedSecret: SharedSecret; sessionProperties: SessionProperties }
    | { __type: 'connecting'; associationKeypair: AssociationKeypair }
    | { __type: 'disconnected' }
    | { __type: 'hello_req_sent'; associationPublicKey: CryptoKey; ecdhPrivateKey: CryptoKey };

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
            const { associationKeypair } = state;
            socket.removeEventListener('open', handleOpen);
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
                        `Failed to connect to the wallet websocket on port ${sessionPort}.`,
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
                    const wallet = new Proxy<MobileWallet>({} as MobileWallet, {
                        get<TMethodName extends keyof MobileWallet>(target: MobileWallet, p: TMethodName) {
                            if (target[p] == null) {
                                const method = p
                                    .toString()
                                    .replace(/[A-Z]/g, (letter) => `_${letter.toLowerCase()}`)
                                    .toLowerCase();
                                target[p] = async function (params: Parameters<MobileWallet[TMethodName]>[0]) {
                                    const id = nextJsonRpcMessageId++;
                                    let rpcMessage = {
                                        id,
                                        jsonrpc: '2.0' as const,
                                        method,
                                        params: params ?? {},
                                    }
                                    switch (p) {
                                        case 'authorize': {
                                            if (sessionProperties.protocol_version === 'legacy' && params) {
                                                let { chain } = params as Parameters<MobileWallet['authorize']>[0];
                                                switch (chain) {
                                                    case 'solana:testnet': { chain = 'testnet'; break; }
                                                    case 'solana:devnet': { chain = `devnet`; break; }
                                                    case 'solana:mainnet': { chain = "mainnet-beta"; break; }
                                                }
                                                (params as any).chain = chain;
                                            }
                                        }
                                        case 'reauthorize': {
                                            const { auth_token, identity } = params as Parameters<MobileWallet['authorize' | 'reauthorize']>[0];
                                            if (auth_token) {
                                                switch (sessionProperties.protocol_version) {
                                                    case 'legacy':
                                                        rpcMessage.method = 'reauthorize';
                                                        rpcMessage.params = { auth_token: auth_token, identity: identity };
                                                        break;
                                                    default:
                                                        rpcMessage.method = 'authorize';
                                                        rpcMessage.params = params ?? {};
                                                        break;
                                                }
                                            }
                                            break;
                                        }
                                    }
                                    socket.send(await encryptJsonRpcMessage(rpcMessage, sharedSecret,));
                                    return new Promise((resolve, reject) => {
                                        jsonRpcResponsePromises[id] = {
                                            resolve(result) {
                                                switch (p) {
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
                                                    case 'getCapabilities': {
                                                        switch (sessionProperties.protocol_version) {
                                                            case 'legacy': {
                                                                result['features'] = [ SolanaSignTransaction ];
                                                                if (result['supports_clone_authorization'] == true) {
                                                                    result['features'].push(SolanaCloneAuthorization);
                                                                }
                                                                break;
                                                            }
                                                            case 'v1': {
                                                                result['supports_sign_and_send_transactions'] = true;
                                                                result['supports_clone_authorization'] = result.features.indexOf(SolanaCloneAuthorization) > -1;
                                                                break;
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
                                } as MobileWallet[TMethodName];
                            }
                            return target[p];
                        },
                        defineProperty() {
                            return false;
                        },
                        deleteProperty() {
                            return false;
                        },
                    });
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
            socket = new WebSocket(websocketURL, [WEBSOCKET_PROTOCOL]);
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
