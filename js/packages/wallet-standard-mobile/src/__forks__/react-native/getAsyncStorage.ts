// Declared structurally so the return type does not drift between the v1 and v2 `AsyncStorage`
// declarations, both of which the optional peer range accepts.
export type AsyncStorageLike = {
    getItem(key: string): Promise<string | null>;
    removeItem(key: string): Promise<void>;
    setItem(key: string, value: string): Promise<void>;
};

/**
 * Narrows a candidate to the {@link AsyncStorageLike} surface the caches call: `getItem`,
 * `removeItem`, and `setItem` must all be functions.
 */
function isUsable(value: unknown): value is AsyncStorageLike {
    if (value == null) {
        return false;
    }
    const candidate = value as AsyncStorageLike;
    return (
        typeof candidate.getItem === 'function' &&
        typeof candidate.removeItem === 'function' &&
        typeof candidate.setItem === 'function'
    );
}

/**
 * Resolves the optional peer dependency, returning `undefined` when it cannot be loaded. The
 * `require` must sit directly inside the `try` block: Metro's default configuration
 * (`transformer.allowOptionalDependencies`) treats exactly that shape as an optional dependency,
 * so apps that never call this keep building without the package installed.
 */
function loadAsyncStorageModule(): unknown {
    try {
        // eslint-disable-next-line @typescript-eslint/no-require-imports
        return require('@react-native-async-storage/async-storage');
    } catch {
        return undefined;
    }
}

/**
 * `@react-native-async-storage/async-storage` is an optional peer dependency, so it is resolved
 * lazily, on first use: apps that supply their own authorization cache never load it and do not
 * need it installed. When it is missing or unusable - not installed, or its native module not
 * linked - this throws a descriptive error instead of degrading into a silently inert cache.
 *
 * The package is CommonJS with a default export, so depending on the resolver the usable instance
 * arrives under `.default` or as the module itself. Both shapes are accepted.
 *
 * @param caller Name of the public function requiring `AsyncStorage`, used in the error message.
 * @param load Test seam for supplying module shapes. Defaults to requiring the real package.
 */
export function getAsyncStorage(caller: string, load: () => unknown = loadAsyncStorageModule): AsyncStorageLike {
    const asyncStorageModule = load();
    const defaultExport = (asyncStorageModule as { default?: unknown } | undefined)?.default;
    const asyncStorage = [defaultExport, asyncStorageModule].find(isUsable);
    if (asyncStorage === undefined) {
        throw new Error(
            `\`${caller}()\` requires \`@react-native-async-storage/async-storage\`, which could not be resolved ` +
                'or is not usable. Check that it is installed and, on bare React Native, that its native module is ' +
                'linked. Otherwise, supply your own authorization cache instead.',
        );
    }
    return asyncStorage;
}
