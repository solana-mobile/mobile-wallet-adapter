import arrayBufferToBase64String from './arrayBufferToBase64String.js';
import { assertAssociationPort } from './associationPort.js';
import { SolanaMobileWalletAdapterError, SolanaMobileWalletAdapterErrorCode } from './errors.js';
import getStringWithURLUnsafeBase64CharactersReplaced from './getStringWithURLUnsafeBase64CharactersReplaced.js';
import { ProtocolVersion } from './types.js';

const INTENT_NAME = 'solana-wallet';

function getPathParts(pathString: string) {
    return (
        pathString
            // Strip leading and trailing slashes
            .replace(/(^\/+|\/+$)/g, '')
            // Return an array of directories
            .split('/')
    );
}

function getIntentURL(methodPathname: string, intentUrlBase?: string) {
    let baseUrl: URL | null = null;
    if (intentUrlBase) {
        try {
            baseUrl = new URL(intentUrlBase);
        } catch {} // eslint-disable-line no-empty
        if (baseUrl?.protocol !== 'https:') {
            throw new SolanaMobileWalletAdapterError(
                SolanaMobileWalletAdapterErrorCode.ERROR_FORBIDDEN_WALLET_BASE_URL,
                'Base URLs supplied by wallets must be valid `https` URLs',
            );
        }
    }
    baseUrl ||= new URL(`${INTENT_NAME}:/`);
    const pathname = methodPathname.startsWith('/')
        ? // Method is an absolute path. Replace it wholesale.
          methodPathname
        : // Method is a relative path. Merge it with the existing one.
          [...getPathParts(baseUrl.pathname), ...getPathParts(methodPathname)].join('/');
    return new URL(pathname, baseUrl);
}

export default async function getAssociateAndroidIntentURL(
    associationPublicKey: CryptoKey,
    putativePort: number,
    associationURLBase?: string,
    protocolVersions: ProtocolVersion[] = ['v1'],
): Promise<URL> {
    const associationPort = assertAssociationPort(putativePort);
    const exportedKey = await crypto.subtle.exportKey('raw', associationPublicKey);
    const encodedKey = arrayBufferToBase64String(exportedKey);
    const url = getIntentURL('v1/associate/local', associationURLBase);
    url.searchParams.set('association', getStringWithURLUnsafeBase64CharactersReplaced(encodedKey));
    url.searchParams.set('port', `${associationPort}`);
    protocolVersions.forEach( (version) => {
        url.searchParams.set('v', version);
    })
    return url;
}
