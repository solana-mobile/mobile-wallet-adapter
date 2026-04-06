import { createDefaultAuthorizationCache as baseCreateDefaultAuthorizationCache } from '@solana-mobile/wallet-standard-mobile';

import { AuthorizationResultCache } from './adapter.js';

export default function createDefaultAuthorizationResultCache(): AuthorizationResultCache {
    return baseCreateDefaultAuthorizationCache();
}
