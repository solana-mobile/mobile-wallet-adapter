// Typescript `enums` thwart tree-shaking. See https://bargsten.org/jsts/enums/
export const SolanaMobileWalletAdapterErrorCode = {
    ERROR_ASSOCIATION_PORT_OUT_OF_RANGE: 'ERROR_ASSOCIATION_PORT_OUT_OF_RANGE',
    ERROR_FORBIDDEN_WALLET_BASE_URL: 'ERROR_FORBIDDEN_WALLET_BASE_URL',
    ERROR_SECURE_CONTEXT_REQUIRED: 'ERROR_SECURE_CONTEXT_REQUIRED',
    ERROR_SESSION_CLOSED: 'ERROR_SESSION_CLOSED',
    ERROR_SESSION_TIMEOUT: 'ERROR_SESSION_TIMEOUT',
    ERROR_WALLET_NOT_FOUND: 'ERROR_WALLET_NOT_FOUND',
    ERROR_INVALID_PROTOCOL_VERSION: 'ERROR_INVALID_PROTOCOL_VERSION',
} as const;
type SolanaMobileWalletAdapterErrorCodeEnum =
    typeof SolanaMobileWalletAdapterErrorCode[keyof typeof SolanaMobileWalletAdapterErrorCode];

type ErrorDataTypeMap = {
    [SolanaMobileWalletAdapterErrorCode.ERROR_ASSOCIATION_PORT_OUT_OF_RANGE]: {
        port: number;
    };
    [SolanaMobileWalletAdapterErrorCode.ERROR_FORBIDDEN_WALLET_BASE_URL]: undefined;
    [SolanaMobileWalletAdapterErrorCode.ERROR_SECURE_CONTEXT_REQUIRED]: undefined;
    [SolanaMobileWalletAdapterErrorCode.ERROR_SESSION_CLOSED]: {
        closeEvent: CloseEvent;
    };
    [SolanaMobileWalletAdapterErrorCode.ERROR_SESSION_TIMEOUT]: undefined;
    [SolanaMobileWalletAdapterErrorCode.ERROR_WALLET_NOT_FOUND]: undefined;
    [SolanaMobileWalletAdapterErrorCode.ERROR_INVALID_PROTOCOL_VERSION]: undefined;
};

export class SolanaMobileWalletAdapterError<TErrorCode extends SolanaMobileWalletAdapterErrorCodeEnum> extends Error {
    data: ErrorDataTypeMap[TErrorCode] | undefined;
    code: TErrorCode;
    constructor(
        ...args: ErrorDataTypeMap[TErrorCode] extends Record<string, unknown>
            ? [code: TErrorCode, message: string, data: ErrorDataTypeMap[TErrorCode]]
            : [code: TErrorCode, message: string]
    ) {
        const [code, message, data] = args;
        super(message);
        this.code = code;
        this.data = data;
        this.name = 'SolanaMobileWalletAdapterError';
    }
}

type JSONRPCErrorCode = number;

// Typescript `enums` thwart tree-shaking. See https://bargsten.org/jsts/enums/
export const SolanaMobileWalletAdapterProtocolErrorCode = {
    // Keep these in sync with `mobilewalletadapter/common/ProtocolContract.java`.
    ERROR_AUTHORIZATION_FAILED: -1,
    ERROR_INVALID_PAYLOADS: -2,
    ERROR_NOT_SIGNED: -3,
    ERROR_NOT_SUBMITTED: -4,
    ERROR_TOO_MANY_PAYLOADS: -5,
    ERROR_ATTEST_ORIGIN_ANDROID: -100,
} as const;
type SolanaMobileWalletAdapterProtocolErrorCodeEnum =
    typeof SolanaMobileWalletAdapterProtocolErrorCode[keyof typeof SolanaMobileWalletAdapterProtocolErrorCode];

type ProtocolErrorDataTypeMap = {
    [SolanaMobileWalletAdapterProtocolErrorCode.ERROR_AUTHORIZATION_FAILED]: undefined;
    [SolanaMobileWalletAdapterProtocolErrorCode.ERROR_INVALID_PAYLOADS]: undefined;
    [SolanaMobileWalletAdapterProtocolErrorCode.ERROR_NOT_SIGNED]: undefined;
    [SolanaMobileWalletAdapterProtocolErrorCode.ERROR_NOT_SUBMITTED]: undefined;
    [SolanaMobileWalletAdapterProtocolErrorCode.ERROR_TOO_MANY_PAYLOADS]: undefined;
    [SolanaMobileWalletAdapterProtocolErrorCode.ERROR_ATTEST_ORIGIN_ANDROID]: {
        attest_origin_uri: string;
        challenge: string;
        context: string;
    };
};

export class SolanaMobileWalletAdapterProtocolError<
    TErrorCode extends SolanaMobileWalletAdapterProtocolErrorCodeEnum,
> extends Error {
    data: ProtocolErrorDataTypeMap[TErrorCode] | undefined;
    code: TErrorCode | JSONRPCErrorCode;
    jsonRpcMessageId: number;
    constructor(
        ...args: ProtocolErrorDataTypeMap[TErrorCode] extends Record<string, unknown>
            ? [
                  jsonRpcMessageId: number,
                  code: TErrorCode | JSONRPCErrorCode,
                  message: string,
                  data: ProtocolErrorDataTypeMap[TErrorCode],
              ]
            : [jsonRpcMessageId: number, code: TErrorCode | JSONRPCErrorCode, message: string]
    ) {
        const [jsonRpcMessageId, code, message, data] = args;
        super(message);
        this.code = code;
        this.data = data;
        this.jsonRpcMessageId = jsonRpcMessageId;
        this.name = 'SolanaMobileWalletAdapterProtocolError';
    }
}
