import { Buffer } from 'buffer'
import { SignInPayload } from './types';
import { SolanaSignInInputWithRequiredFields, createSignInMessageText } from '@solana/wallet-standard-util';

export function createDefaultSolanaSignInParams(): Pick<SignInPayload, 'version' | 'chainId' | 'nonce' | 'issuedAt' > {
    return {
        version: '1',
        chainId: '1',
        nonce: Buffer.from(
            crypto.getRandomValues(new Uint8Array(8))
        ).toString('hex'),
        issuedAt: new Date().toISOString()
    }
}

export function createSIWSMessage(payload: SolanaSignInInputWithRequiredFields & SignInPayload): string {
    return createSignInMessageText({ ...payload, chainId: payload?.chainId?.toString() })
}

export function createSIWSMessageBase64(payload: SolanaSignInInputWithRequiredFields & SignInPayload): string {
    return Buffer.from(createSIWSMessage(payload)).toString('base64')
}
