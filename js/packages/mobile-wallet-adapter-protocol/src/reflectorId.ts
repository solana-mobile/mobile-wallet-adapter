import { SolanaMobileWalletAdapterError, SolanaMobileWalletAdapterErrorCode } from './errors.js';

declare const tag: unique symbol;
export type ReflectorId = number & { readonly [tag]: 'ReflectorId' };

export function getRandomReflectorId(): ReflectorId {
    return assertReflectorId(Math.floor(Math.random()*9007199254740992)); // 0 < id < 2^53 - 1
}

export function assertReflectorId(id: number): ReflectorId {
    if (id < 0 || id > 9007199254740991) { // 0 < id < 2^53 - 1
        throw new SolanaMobileWalletAdapterError(
            SolanaMobileWalletAdapterErrorCode.ERROR_REFLECTOR_ID_OUT_OF_RANGE,
            `Association port number must be between 49152 and 65535. ${id} given.`,
            { id },
        );
    }
    return id as ReflectorId;
}