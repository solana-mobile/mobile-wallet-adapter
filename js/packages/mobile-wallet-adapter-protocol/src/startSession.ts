import { AssociationPort, getRandomAssociationPort } from './associationPort.js';
import { SolanaMobileWalletAdapterError, SolanaMobileWalletAdapterErrorCode } from './errors.js';
import getAssociateAndroidIntentURL from './getAssociateAndroidIntentURL.js';

// Typescript `enums` thwart tree-shaking. See https://bargsten.org/jsts/enums/
const Browser = {
    Firefox: 0,
    Other: 1,
} as const;
type BrowserEnum = typeof Browser[keyof typeof Browser];

function assertUnreachable(x: never): never {
    return x;
}

function getBrowser(): BrowserEnum {
    return navigator.userAgent.indexOf('Firefox/') !== -1 ? Browser.Firefox : Browser.Other;
}

function getDetectionPromise() {
    // Chrome and others silently fail if a custom protocol is not supported.
    // For these, we wait to see if the browser is navigated away from in
    // a reasonable amount of time (ie. the native wallet opened).
    return new Promise<void>((resolve, reject) => {
        function cleanup() {
            clearTimeout(timeoutId);
            window.removeEventListener('blur', handleBlur);
        }
        function handleBlur() {
            cleanup();
            resolve();
        }
        window.addEventListener('blur', handleBlur);
        const timeoutId = setTimeout(() => {
            cleanup();
            reject();
        }, 2000);
    });
}

let _frame: HTMLIFrameElement | null = null;
function launchUrlThroughHiddenFrame(url: URL) {
    if (_frame == null) {
        _frame = document.createElement('iframe');
        _frame.style.display = 'none';
        document.body.appendChild(_frame);
    }
    // eslint-disable-next-line @typescript-eslint/no-non-null-assertion
    _frame.contentWindow!.location.href = url.toString();
}

export async function startSession(
    associationPublicKey: CryptoKey,
    associationURLBase?: string,
): Promise<AssociationPort> {
    const randomAssociationPort = getRandomAssociationPort();
    const associationUrl = await getAssociateAndroidIntentURL(
        associationPublicKey,
        randomAssociationPort,
        associationURLBase,
    );
    if (associationUrl.protocol === 'https:') {
        // The association URL is an Android 'App Link' or iOS 'Universal Link'.
        // These are regular web URLs that are designed to launch an app if it
        // is installed or load the actual target webpage if not.
        window.location.assign(associationUrl);
    } else {
        // The association URL has a custom protocol (eg. `solana-wallet:`)
        try {
            const browser = getBrowser();
            switch (browser) {
                case Browser.Firefox:
                    // If a custom protocol is not supported in Firefox, it throws.
                    launchUrlThroughHiddenFrame(associationUrl);
                    // If we reached this line, it's supported.
                    break;
                case Browser.Other: {
                    const detectionPromise = getDetectionPromise();
                    window.location.assign(associationUrl);
                    await detectionPromise;
                    break;
                }
                default:
                    assertUnreachable(browser);
            }
        } catch (e) {
            throw new SolanaMobileWalletAdapterError(
                SolanaMobileWalletAdapterErrorCode.ERROR_WALLET_NOT_FOUND,
                'Found no installed wallet that supports the mobile wallet protocol.',
            );
        }
    }
    return randomAssociationPort;
}
