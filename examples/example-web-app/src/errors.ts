/** An error named like the @solana/wallet-adapter-base error it stands in for, e.g. `WalletSignMessageError`. */
export class WalletError extends Error {
    constructor(name: string, message = '', cause?: unknown) {
        super(message, { cause });
        this.name = name;
    }
}

/** Runs a wallet call, wrapping whatever it throws in a `WalletError` called `name`. */
export async function walletCall<T>(name: string, call: () => Promise<T>): Promise<T> {
    try {
        return await call();
    } catch (error) {
        throw new WalletError(name, '', error);
    }
}

/**
 * A readable description of anything thrown or rejected, never `[object Object]`. Wallets and the MWA
 * transport reject with `Error`s (with a `cause` chain and `code`), but also with `Event`s, plain
 * objects, strings or nothing at all.
 */
export function describeError(error: unknown): string {
    if (error instanceof Error) {
        const label = error.name === 'Error' ? '' : error.name;
        const code = (error as { code?: unknown }).code;
        const text = [label, error.message].filter(Boolean).join(': ') + (code === undefined ? '' : ` (${code})`);
        if (error.cause === undefined) return text || 'Unknown error';
        const cause = describeError(error.cause);
        // Wrappers often repeat their cause's message; say it once.
        if (!error.message || cause.includes(error.message)) return label ? `${label}: ${cause}` : cause;
        return `${text}: ${cause}`;
    }
    if (error === undefined || error === null || error === '') return 'Unknown error';
    if (typeof error === 'string') return error;
    if (typeof Event !== 'undefined' && error instanceof Event) return `${error.type} event`;
    if (typeof error === 'object') {
        const { message, code } = error as { message?: unknown; code?: unknown };
        if (typeof message === 'string' && message) return code === undefined ? message : `${message} (${code})`;
        try {
            return JSON.stringify(error);
        } catch {
            // Circular or otherwise unserializable.
            return `Unreadable error with keys: ${Object.keys(error).join(', ') || 'none'}`;
        }
    }
    return String(error);
}
