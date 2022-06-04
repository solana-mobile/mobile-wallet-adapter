import arrayBufferToBase64String from './arrayBufferToBase64String';
import { assertAssociationPort } from './associationPort';
import getStringWithURLUnsafeBase64CharactersReplaced from './getStringWithURLUnsafeBase64CharactersReplaced';

const INTENT_NAME = 'solana-wallet';

function getIntentURL(method: string) {
    const base = `${INTENT_NAME}:/`;
    return new URL(method, base);
}

export default async function getAssociateAndroidIntentURL(
    associationPublicKey: CryptoKey,
    putativePort: number,
): Promise<URL> {
    const associationPort = assertAssociationPort(putativePort);
    const exportedKey = await crypto.subtle.exportKey('raw', associationPublicKey);
    const encodedKey = arrayBufferToBase64String(exportedKey);
    const url = getIntentURL('v1/associate/local');
    url.searchParams.set('association', getStringWithURLUnsafeBase64CharactersReplaced(encodedKey));
    url.searchParams.set('port', `${associationPort}`);
    return url;
}
