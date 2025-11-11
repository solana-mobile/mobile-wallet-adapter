import { Base64EncodedAddress } from "@solana-mobile/mobile-wallet-adapter-protocol";
import { Address, getAddressDecoder } from "@solana/kit";
import { toUint8Array } from "js-base64";


export function getPublicKeyFromAddress(base64Address: Base64EncodedAddress): Address {
    const publicKeyByteArray = toUint8Array(base64Address)
    const addressDecoder = getAddressDecoder()
    return addressDecoder.decode(publicKeyByteArray)
  }