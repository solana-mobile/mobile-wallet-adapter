import { Buffer } from 'buffer'
import {
    SolanaSignInInputWithRequiredFields, 
    createSignInMessageText,
} from '@solana/wallet-standard-util';
import { SignInPayload } from './types';

export function createSIWSMessage(payload: SolanaSignInInputWithRequiredFields & SignInPayload): string {
    return createSignInMessageText(payload)
}

export function createSIWSMessageBase64(payload: SolanaSignInInputWithRequiredFields & SignInPayload): string {
    return Buffer.from(createSIWSMessage(payload)).toString('base64')
}