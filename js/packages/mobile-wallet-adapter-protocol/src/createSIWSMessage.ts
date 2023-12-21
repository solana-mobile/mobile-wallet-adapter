import {
    SolanaSignInInputWithRequiredFields, 
    createSignInMessageText,
} from '@solana/wallet-standard-util';
import { SignInPayload } from './types';
import { encode } from './base64Utils';

export function createSIWSMessage(payload: SolanaSignInInputWithRequiredFields & SignInPayload): string {
    return createSignInMessageText(payload);
}

export function createSIWSMessageBase64(payload: SolanaSignInInputWithRequiredFields & SignInPayload): string {
    return encode(createSIWSMessage(payload));
}