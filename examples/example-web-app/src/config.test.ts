import { describe, expect, it } from 'vitest';

import { readConfig } from './config.js';

describe('readConfig', () => {
    it('uses the build environment and the devnet RPC by default', () => {
        expect(
            readConfig('', { VITE_NOSTR_RELAY: 'relay.test', VITE_REFLECTOR_HOST_AUTHORITY: 'reflector.test' }),
        ).toEqual({
            nostrRelay: 'relay.test',
            reflectorHostAuthority: 'reflector.test',
            rpcUrl: 'https://api.devnet.solana.com',
        });
    });

    it('lets URL parameters override the environment', () => {
        expect(
            readConfig('?relay=localhost:7447&reflector=r.local&rpc=http://localhost:8899', {
                VITE_NOSTR_RELAY: 'relay.test',
            }),
        ).toEqual({
            nostrRelay: 'localhost:7447',
            reflectorHostAuthority: 'r.local',
            rpcUrl: 'http://localhost:8899',
        });
    });

    it('treats empty values as unset', () => {
        expect(readConfig('?relay=&rpc=', { VITE_NOSTR_RELAY: '' })).toEqual({
            nostrRelay: undefined,
            reflectorHostAuthority: undefined,
            rpcUrl: 'https://api.devnet.solana.com',
        });
    });
});
