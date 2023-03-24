import {Keypair, Ed25519SecretKey} from '@solana/web3.js';

type Result = {
    signedPayload: Uint8Array,
    signature: Uint8Array
};

export class SolanaSigningUseCase {
  static readonly SIGNATURE_LEN = 64;
  static readonly PUBLIC_KEY_LEN = 32;

  static signTransaction(
    transaction: Uint8Array,
    keypair: Keypair
  ): Result {
    console.log("In sign transaction use case")
    return {signedPayload: new Uint8Array([0]), signature: new Uint8Array([0])}
  }

  static signMessage(
    transaction: Uint8Array,
    keypair: Keypair
  ): Result {

    return {signedPayload: new Uint8Array([0]), signature: new Uint8Array([0])}
  }
}