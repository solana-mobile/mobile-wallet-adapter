import { AuthorizationResultCache } from './adapter.js';
import { createDefaultAuthorizationResultCache as baseCreateDefaultAuthorizationResultCache } from '@solana-mobile/wallet-standard-mobile';

export default function createDefaultAuthorizationResultCache(): AuthorizationResultCache {
    return baseCreateDefaultAuthorizationResultCache();
}
