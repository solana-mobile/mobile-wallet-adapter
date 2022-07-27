import { AddressSelector } from './adapter';

export default function createDefaultAddressSelector(): AddressSelector {
    return {
        async select(addresses) {
            return addresses[0];
        },
    };
}
