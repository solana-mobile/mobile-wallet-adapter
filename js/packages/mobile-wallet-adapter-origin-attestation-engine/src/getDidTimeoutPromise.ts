// How long to wait for the 'ready' event *after* the attestation engine endpoint's load event has fired.
const READY_WAIT_TIMEOUT_MS = 2000;

export default async function getDidTimeoutPromise(contentWindow: Window, abortSignal: AbortSignal) {
    return new Promise((_, reject) => {
        let timeoutId: NodeJS.Timeout | undefined;
        function cleanup() {
            abortSignal.removeEventListener('abort', handleAbort);
            contentWindow.removeEventListener('load', handleLoad);
            clearTimeout(timeoutId);
        }
        function handleAbort() {
            cleanup();
        }
        abortSignal.addEventListener('abort', handleAbort);
        function handleLoad() {
            cleanup();
            timeoutId = setTimeout(() => {
                cleanup();
                reject(new Error('TODO: timeout'));
            }, READY_WAIT_TIMEOUT_MS);
        }
        contentWindow.addEventListener('load', handleLoad);
    });
}
