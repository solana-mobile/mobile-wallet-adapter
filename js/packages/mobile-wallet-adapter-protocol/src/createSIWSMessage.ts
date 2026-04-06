import { createSignInMessageText, SolanaSignInInputWithRequiredFields } from '@solana/wallet-standard-util';

import { encode } from './base64Utils.js';
import { SignInPayload } from './types.js';

export function createSIWSMessage(payload: SolanaSignInInputWithRequiredFields & SignInPayload): string {
    return createSignInMessageText(payload);
}

export function createSIWSMessageBase64Url(payload: SolanaSignInInputWithRequiredFields & SignInPayload): string {
    return encode(createSIWSMessage(payload)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, ''); // convert to base64url encoding;
}
