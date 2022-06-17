import { SolanaMobileWalletAdapterProtocolAssociationPortOutOfRangeError } from './errors';

declare const tag: unique symbol;
export type AssociationPort = number & { readonly [tag]: 'AssociationPort' };

export function getRandomAssociationPort(): AssociationPort {
    return assertAssociationPort(49152 + Math.floor(Math.random() * (65535 - 49152 + 1)));
}

export function assertAssociationPort(port: number): AssociationPort {
    if (port < 49152 || port > 65535) {
        throw new SolanaMobileWalletAdapterProtocolAssociationPortOutOfRangeError(port);
    }
    return port as AssociationPort;
}
