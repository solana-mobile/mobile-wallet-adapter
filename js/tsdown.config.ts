import { existsSync } from 'node:fs';
import path from 'node:path';

import { defineConfig, type DtsOptions, type UserConfig } from 'tsdown';

type Runtime = 'browser' | 'node' | 'react-native';
type TsdownPlugin = NonNullable<UserConfig['plugins']>;

const DTS_OPTIONS: DtsOptions = {
    emitDtsOnly: true,
    sourcemap: true,
};
const NODE_ENV_DEFINE_VALUE = process.env.NODE_ENV === undefined ? 'undefined' : JSON.stringify(process.env.NODE_ENV);
const OPTIONAL_ENTRY_NAMES = ['encoding'];

const SOURCE_CANDIDATE_EXTENSIONS = ['.cts', '.mts', '.ts', '.tsx'];
const SOURCE_INDEX_EXTENSIONS = SOURCE_CANDIDATE_EXTENSIONS.map((extension) => `index${extension}`);

function resolveSourcePath(importer: string, source: string): string | undefined {
    const resolvedImportPath = path.resolve(path.dirname(importer), source);
    const extension = path.extname(resolvedImportPath);
    const candidates =
        extension === '.cjs' || extension === '.js' || extension === '.jsx' || extension === '.mjs'
            ? SOURCE_CANDIDATE_EXTENSIONS.map(
                  (candidateExtension) => `${resolvedImportPath.slice(0, -extension.length)}${candidateExtension}`,
              )
            : [
                  resolvedImportPath,
                  ...SOURCE_CANDIDATE_EXTENSIONS.map(
                      (candidateExtension) => `${resolvedImportPath}${candidateExtension}`,
                  ),
                  ...SOURCE_INDEX_EXTENSIONS.map((indexFilename) => path.join(resolvedImportPath, indexFilename)),
              ];
    return candidates.find((candidate) => existsSync(candidate));
}

function runtimeForkPlugin(runtime: Runtime): TsdownPlugin {
    return {
        name: `runtime-fork-${runtime}`,
        resolveId(source: string, importer?: string) {
            if (importer == null || !source.startsWith('.')) {
                return null;
            }
            const resolvedSourcePath = resolveSourcePath(importer, source);
            if (resolvedSourcePath == null) {
                return null;
            }
            const forkPath = path.join(
                path.dirname(resolvedSourcePath),
                '__forks__',
                runtime,
                path.basename(resolvedSourcePath),
            );
            return existsSync(forkPath) ? forkPath : null;
        },
    };
}

function createEntry(entryName: string): Record<string, string> {
    const entry: Record<string, string> = {
        [entryName]: 'src/index.ts',
    };
    const runtimeSuffix = entryName.slice('index'.length);
    OPTIONAL_ENTRY_NAMES.forEach((optionalEntryName) => {
        const optionalEntryPath = `src/${optionalEntryName}.ts`;
        if (existsSync(path.join(process.cwd(), optionalEntryPath))) {
            entry[`${optionalEntryName}${runtimeSuffix}`] = optionalEntryPath;
        }
    });
    return entry;
}

function platformForRuntime(runtime: Runtime): NonNullable<UserConfig['platform']> {
    switch (runtime) {
        case 'browser':
            return 'browser';
        case 'node':
            return 'node';
        case 'react-native':
            return 'neutral';
    }
}

function createConfig({ entryName, runtime }: { entryName: string; runtime: Runtime }): UserConfig {
    // Every runtime build lands in `lib/esm`, so chunks carry the same suffix as their entry
    // (`index.js` / `index.browser.js` / `index.native.js`) to keep the builds from overwriting each other.
    const runtimeSuffix = entryName.slice('index'.length);
    return {
        clean: false,
        cwd: process.cwd(),
        define: {
            'process.env.BROWSER': JSON.stringify(runtime === 'browser'),
            'process.env.NODE_ENV': NODE_ENV_DEFINE_VALUE,
        },
        deps: {
            onlyBundle: false,
            skipNodeModulesBundle: true,
        },
        dts: false,
        entry: createEntry(entryName),
        format: 'esm',
        hash: false,
        outDir: 'lib/esm',
        outExtensions: () => ({
            dts: '.d.ts',
            js: '.js',
        }),
        outputOptions: {
            chunkFileNames: `chunks/[name]${runtimeSuffix}.js`,
        },
        platform: platformForRuntime(runtime),
        plugins: [runtimeForkPlugin(runtime)],
        sourcemap: true,
        tsconfig: 'tsconfig.json',
    };
}

function createDtsConfig(): UserConfig {
    return {
        clean: false,
        cwd: process.cwd(),
        deps: {
            onlyBundle: false,
        },
        dts: DTS_OPTIONS,
        entry: createEntry('index'),
        format: 'esm',
        hash: false,
        outDir: 'lib/types',
        outExtensions: () => ({
            dts: '.d.ts',
            js: '.js',
        }),
        plugins: [runtimeForkPlugin('node')],
        sourcemap: true,
        tsconfig: 'tsconfig.json',
    };
}

export default defineConfig([
    createConfig({
        entryName: 'index',
        runtime: 'node',
    }),
    createConfig({
        entryName: 'index.browser',
        runtime: 'browser',
    }),
    createConfig({
        entryName: 'index.native',
        runtime: 'react-native',
    }),
    createDtsConfig(),
]);
