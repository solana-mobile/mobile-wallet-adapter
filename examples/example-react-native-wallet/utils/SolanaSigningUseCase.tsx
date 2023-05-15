import {Keypair, VersionedTransaction, Signer} from '@solana/web3.js';
import {sign} from '@solana/web3.js/src/utils/ed25519';

export class SolanaSigningUseCase {
  static readonly SIGNATURE_LEN = 64;
  static readonly PUBLIC_KEY_LEN = 32;

  static signTransaction(
    transactionByteArray: Uint8Array,
    keypair: Keypair,
  ): Uint8Array {
    const transaction: VersionedTransaction =
      VersionedTransaction.deserialize(transactionByteArray);
    const signer: Signer = {
      publicKey: keypair.publicKey,
      secretKey: keypair.secretKey,
    };
    transaction.sign([signer]);
    return transaction.serialize();
  }

  static signMessage(
    messageByteArray: Uint8Array,
    keypair: Keypair,
  ): Uint8Array {
    return sign(messageByteArray, keypair.secretKey.slice(0, 32));
  }
}
