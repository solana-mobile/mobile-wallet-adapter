/// <reference types="vite/client" />

interface ImportMetaEnv {
    readonly VITE_NOSTR_RELAY?: string;
    readonly VITE_REFLECTOR_HOST_AUTHORITY?: string;
}
