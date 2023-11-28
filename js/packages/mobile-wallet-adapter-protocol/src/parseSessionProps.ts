import { decryptMessage } from "./encryptedMessage";
import { SolanaMobileWalletAdapterError, SolanaMobileWalletAdapterErrorCode } from "./errors";
import { SharedSecret } from "./parseHelloRsp";
import { ProtocolVersion, SessionProperties } from "./types";

export default async function parseSessionProps(
    message: ArrayBuffer, 
    sharedSecret: SharedSecret
): Promise<SessionProperties> {
    const plaintext = await decryptMessage(message, sharedSecret);
    const jsonProperties = JSON.parse(plaintext);
    let protocolVersion: ProtocolVersion = 'legacy';
    if (Object.hasOwnProperty.call(jsonProperties, 'v')) {
        switch (jsonProperties.v) {
            case 1:
            case '1':
            case 'v1':
                protocolVersion = 'v1'
                break;
            case 'legacy':
                protocolVersion = 'legacy'
                break;
            default:
                throw new SolanaMobileWalletAdapterError(
                    SolanaMobileWalletAdapterErrorCode.ERROR_INVALID_PROTOCOL_VERSION, 
                    `Unknown/unsupported protocol version: ${jsonProperties.v}`
                );
        }
    }
    return <SessionProperties>({
        protocol_version: protocolVersion
    })
}