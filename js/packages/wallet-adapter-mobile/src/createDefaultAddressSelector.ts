import { AddressSelector } from './adapter.js';

export function createDefaultAddressSelector(): AddressSelector {
    return {
        async select(addresses) {
            return addresses[0];
        },
    };
}

/**
 * @deprecated Use {@link createDefaultAddressSelector} instead.
 */
export default createDefaultAddressSelector;
