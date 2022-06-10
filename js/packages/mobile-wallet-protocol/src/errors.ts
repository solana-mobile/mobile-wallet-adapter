export class SolanaMobileWalletAdapterAssociationPortOutOfRangeError extends Error {
    constructor(port: number) {
        super(`Association port number must be between 49152 and 65535. ${port} given.`);
        this.name = 'SolanaMobileWalletAdapterAssociationPortOutOfRangeError';
    }
}

export class SolanaMobileWalletAdapterSessionClosedError extends Error {
    constructor(code: number, reason: string) {
        super(`The wallet connection dropped unexpectedly (${code}: ${reason}).`);
        this.name = 'SolanaMobileWalletAdapterSessionClosedError';
    }
}

export class SolanaMobileWalletAdapterReauthorizeError extends Error {
    constructor() {
        super('The auth token provided has gone stale and needs reauthorization.');
        this.name = 'SolanaMobileWalletAdapterReauthorizeError';
    }
}

export class SolanaMobileWalletAdapterJsonRpcError extends Error {
    code: number;
    jsonRpcMessageId: number;
    constructor(jsonRpcMessageId: number, code: number, message: string) {
        super(message);
        this.code = code;
        this.jsonRpcMessageId = jsonRpcMessageId;
        this.name = 'SolanaNativeWalletAdapterJsonRpcError';
    }
}
