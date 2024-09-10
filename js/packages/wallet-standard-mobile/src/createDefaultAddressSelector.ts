import { AddressSelector } from './wallet.js';

export default function createDefaultAddressSelector(): AddressSelector {
    return {
        async select(addresses) {
            return addresses[0];
        },
    };
}
