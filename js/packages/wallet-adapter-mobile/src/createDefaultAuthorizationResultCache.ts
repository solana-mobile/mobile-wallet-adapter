import { AuthorizationResultCache } from './adapter.js';
import { createDefaultAuthorizationCache as baseCreateDefaultAuthorizationCache } from '@solana-mobile/wallet-standard-mobile';

export default function createDefaultAuthorizationResultCache(): AuthorizationResultCache {
    return baseCreateDefaultAuthorizationCache();
}
