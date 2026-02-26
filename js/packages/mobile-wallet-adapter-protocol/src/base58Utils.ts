import { getBase58Decoder } from "@solana/codecs-strings";
import { toUint8Array } from "./base64Utils";

export function fromUint8Array(byteArray: Uint8Array): string {
    return getBase58Decoder().decode(byteArray);
}

export function base64ToBase58(base64EncodedString: string): string {
    return fromUint8Array(toUint8Array(base64EncodedString));
}