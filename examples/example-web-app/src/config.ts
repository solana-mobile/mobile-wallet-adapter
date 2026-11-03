/**
 * Example app configuration. The relay settings come from the build environment and can be
 * overridden per URL, so one build serves a phone, a desktop browser and the e2e harness:
 *
 *   ?relay=<domain>       Nostr relay domain (remote association over Nostr)
 *   ?reflector=<host>     reflector host authority (remote association over a reflector)
 *   ?rpc=<url>            Solana JSON-RPC endpoint
 */
export interface ExampleConfig {
    nostrRelay: string | undefined;
    reflectorHostAuthority: string | undefined;
    rpcUrl: string;
}

export const CHAIN = 'solana:devnet';
export const EXPLORER_CLUSTER = 'devnet';
const DEFAULT_RPC_URL = 'https://api.devnet.solana.com';

export function readConfig(search: string, env: Record<string, string | undefined>): ExampleConfig {
    const params = new URLSearchParams(search);
    return {
        nostrRelay: params.get('relay') || env.VITE_NOSTR_RELAY || undefined,
        reflectorHostAuthority: params.get('reflector') || env.VITE_REFLECTOR_HOST_AUTHORITY || undefined,
        rpcUrl: params.get('rpc') || DEFAULT_RPC_URL,
    };
}

export const config = readConfig(window.location.search, import.meta.env);
