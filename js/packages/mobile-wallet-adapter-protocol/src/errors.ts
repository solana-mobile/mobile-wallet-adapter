export class SolanaMobileWalletAdapterSecureContextRequiredError extends Error {
    constructor() {
        super('The mobile wallet adapter protocol must be used in a secure context (`https`).');
        this.name = 'SolanaMobileWalletAdapterSecureContextRequiredError';
    }
}

export class SolanaMobileWalletAdapterForbiddenWalletBaseURLError extends Error {
    constructor() {
        super('Base URLs supplied by wallets must be valid `https` URLs');
        this.name = 'SolanaMobileWalletAdapterForbiddenWalletBaseURLError';
    }
}

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

type JSONRPCErrorCode = number;

export enum SolanaMobileWalletAdapterProtocolError {
    ERROR_REAUTHORIZE = -1,
    ERROR_AUTHORIZATION_FAILED = -2,
    ERROR_INVALID_PAYLOAD = -3,
    ERROR_NOT_SIGNED = -4,
    ERROR_NOT_COMMITTED = -5,
    ERROR_ATTEST_ORIGIN_ANDROID = -100,
}

type ErrorDataTypeMap = {
    [SolanaMobileWalletAdapterProtocolError.ERROR_ATTEST_ORIGIN_ANDROID]: {
        attest_origin_uri: string;
        challenge: string;
        context: string;
    };
};

export class SolanaMobileWalletAdapterProtocolJsonRpcError<TErrorCode extends keyof ErrorDataTypeMap> extends Error {
    data: ErrorDataTypeMap[TErrorCode] extends Record<string, unknown> ? ErrorDataTypeMap[TErrorCode] : undefined;
    code: SolanaMobileWalletAdapterProtocolError | JSONRPCErrorCode;
    jsonRpcMessageId: number;
    constructor(
        ...args: ErrorDataTypeMap[TErrorCode] extends Record<string, unknown>
            ? [
                  jsonRpcMessageId: number,
                  code: SolanaMobileWalletAdapterProtocolError | JSONRPCErrorCode,
                  message: string,
                  data: ErrorDataTypeMap[TErrorCode],
              ]
            : [
                  jsonRpcMessageId: number,
                  code: SolanaMobileWalletAdapterProtocolError | JSONRPCErrorCode,
                  message: string,
              ]
    ) {
        const [jsonRpcMessageId, code, message, data] = args;
        super(message);
        this.code = code;
        this.data = data as ErrorDataTypeMap[TErrorCode] extends Record<string, unknown>
            ? ErrorDataTypeMap[TErrorCode]
            : undefined;
        this.jsonRpcMessageId = jsonRpcMessageId;
        this.name = 'SolanaNativeWalletAdapterJsonRpcError';
    }
}
