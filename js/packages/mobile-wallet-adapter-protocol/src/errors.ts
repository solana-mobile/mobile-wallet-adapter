export class SolanaMobileWalletAdapterWalletNotInstalledError extends Error {
    constructor() {
        super(`Found no installed wallet that supports the mobile wallet protocol.`);
        this.name = 'SolanaMobileWalletAdapterWalletNotInstalledError';
    }
}

export class SolanaMobileWalletAdapterProtocolSessionEstablishmentError extends Error {
    constructor(port: number) {
        super(`Failed to connect to the wallet websocket on port ${port}.`);
        this.name = 'SolanaMobileWalletAdapterProtocolSessionEstablishmentError';
    }
}

export class SolanaMobileWalletAdapterProtocolAssociationPortOutOfRangeError extends Error {
    constructor(port: number) {
        super(`Association port number must be between 49152 and 65535. ${port} given.`);
        this.name = 'SolanaMobileWalletAdapterProtocolAssociationPortOutOfRangeError';
    }
}

export class SolanaMobileWalletAdapterProtocolSessionClosedError extends Error {
    constructor(code: number, reason: string) {
        super(`The wallet session dropped unexpectedly (${code}: ${reason}).`);
        this.name = 'SolanaMobileWalletAdapterProtocolSessionClosedError';
    }
}

export class SolanaMobileWalletAdapterProtocolReauthorizeError extends Error {
    constructor() {
        super('The auth token provided has gone stale and needs reauthorization.');
        this.name = 'SolanaMobileWalletAdapterProtocolReauthorizeError';
    }
}

export class SolanaMobileWalletAdapterProtocolJsonRpcError extends Error {
    code: number;
    jsonRpcMessageId: number;
    constructor(jsonRpcMessageId: number, code: number, message: string) {
        super(message);
        this.code = code;
        this.jsonRpcMessageId = jsonRpcMessageId;
        this.name = 'SolanaNativeWalletAdapterJsonRpcError';
    }
}
