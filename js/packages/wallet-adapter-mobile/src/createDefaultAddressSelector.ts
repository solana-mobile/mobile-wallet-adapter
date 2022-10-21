import { AddressSelector } from './adapter.js';

export default function createDefaultAddressSelector(): AddressSelector {
    return {
        async select(addresses) {
            return addresses[0];
        },
    };
}
