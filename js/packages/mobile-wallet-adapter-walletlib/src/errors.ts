export enum SolanaMWAWalletLibErrorCode {
    ERROR_INTENT_DATA_NOT_FOUND = 'ERROR_INTENT_DATA_NOT_FOUND',
    ERROR_SESSION_ALREADY_CREATED = 'ERROR_SESSION_ALREADY_CREATED',
    ERROR_UNSUPPORTED_ASSOCIATION_URI = 'ERROR_UNSUPPORTED_ASSOCIATION_URI',
    ERROR_UNSUPPORTED_ASSOCIATION_TYPE = 'ERROR_UNSUPPORTED_ASSOCIATION_TYPE',
}

export class SolanaMWAWalletLibError extends Error {
    code: SolanaMWAWalletLibErrorCode;
    constructor(code: SolanaMWAWalletLibErrorCode, message: string) {
        super(message);
        this.name = 'SolanaMWAWalletLibError';
        this.code = code;
    }
}
