import { SolanaMobileWalletAdapterError, SolanaMobileWalletAdapterErrorCode } from './errors.js';

declare const tag: unique symbol;
export type AssociationPort = number & { readonly [tag]: 'AssociationPort' };

export function getRandomAssociationPort(): AssociationPort {
    return assertAssociationPort(49152 + Math.floor(Math.random() * (65535 - 49152 + 1)));
}

export function assertAssociationPort(port: number): AssociationPort {
    if (port < 49152 || port > 65535) {
        throw new SolanaMobileWalletAdapterError(
            SolanaMobileWalletAdapterErrorCode.ERROR_ASSOCIATION_PORT_OUT_OF_RANGE,
            `Association port number must be between 49152 and 65535. ${port} given.`,
            { port },
        );
    }
    return port as AssociationPort;
}
