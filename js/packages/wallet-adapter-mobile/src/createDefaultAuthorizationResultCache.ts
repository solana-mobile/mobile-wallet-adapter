import { createDefaultAuthorizationCache as baseCreateDefaultAuthorizationCache } from '@solana-mobile/wallet-standard-mobile';

import { AuthorizationResultCache } from './adapter.js';

export function createDefaultAuthorizationResultCache(): AuthorizationResultCache {
    return baseCreateDefaultAuthorizationCache();
}

/**
 * @deprecated Use {@link createDefaultAuthorizationResultCache} instead.
 */
export default createDefaultAuthorizationResultCache;
