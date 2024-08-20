import { SolanaMobileWalletAdapterError, SolanaMobileWalletAdapterErrorCode } from './errors.js';

declare const tag: unique symbol;
export type ReflectorId = number & { readonly [tag]: 'ReflectorId' };

export function getRandomReflectorId(): ReflectorId {
    return assertReflectorId(getRandomInt(0, 9007199254740991)); // 0 < id < 2^53 - 1
}

function getRandomInt(min: number, max: number) {
    const randomBuffer = new Uint32Array(1);

    window.crypto.getRandomValues(randomBuffer);

    let randomNumber = randomBuffer[0] / (0xffffffff + 1);

    min = Math.ceil(min);
    max = Math.floor(max);
    return Math.floor(randomNumber * (max - min + 1)) + min;
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