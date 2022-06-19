import { AssociationPort, getRandomAssociationPort } from './associationPort';
import { SolanaMobileWalletAdapterWalletNotInstalledError } from './errors';
import getAssociateAndroidIntentURL from './getAssociateAndroidIntentURL';

enum Browser {
    Firefox,
    Other,
}

function getBrowser(): Browser {
    return navigator.userAgent.indexOf('Firefox/') !== -1 ? Browser.Firefox : Browser.Other;
}

let _frame: HTMLIFrameElement;
function launchUrlThroughHiddenFrame(url: URL) {
    if (_frame == null) {
        _frame = document.createElement('iframe');
        _frame.style.display = 'none';
        document.body.appendChild(_frame);
    }
    _frame.contentWindow!.location.assign(url);
}

export async function startSession(associationPublicKey: CryptoKey): Promise<AssociationPort> {
    let detectionPromise: Promise<void> | undefined;
    const randomAssociationPort = getRandomAssociationPort();
    const associationUrl = await getAssociateAndroidIntentURL(associationPublicKey, randomAssociationPort);
    const browser = getBrowser();
    try {
        switch (browser) {
            case Browser.Firefox:
                // If a custom protocol is not supported in Firefox, it throws.
                launchUrlThroughHiddenFrame(associationUrl);
                // If we reached this line, it's supported.
                break;
            case Browser.Other:
                // Other browsers silently fail if a custom protocol is not supported.
                // For these, we wait to see if the browser is navigated away from in
                // a reasonable amount of time (ie. the native wallet opened).
                detectionPromise = new Promise<void>((resolve, reject) => {
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
                    }, 500);
                });
                window.location.replace(associationUrl);
                await detectionPromise;
                break;
            default:
                // Exhaustive switch check.
                // eslint-disable-next-line no-case-declarations
                const _: never = browser; // eslint-disable-line @typescript-eslint/no-unused-vars
        }
    } catch (e) {
        throw new SolanaMobileWalletAdapterWalletNotInstalledError();
    }
    return randomAssociationPort;
}
