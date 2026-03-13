import { existsSync } from 'node:fs';
import path from 'node:path';
import { defineConfig, type DtsOptions, type UserConfig } from 'tsdown';

type Runtime = 'node' | 'react-native';
type TsdownPlugin = NonNullable<UserConfig['plugins']>[number];

const DTS_OPTIONS: DtsOptions = {
    emitDtsOnly: true,
    sourcemap: true,
};
const NODE_ENV_DEFINE_VALUE = process.env.NODE_ENV === undefined ? 'undefined' : JSON.stringify(process.env.NODE_ENV);

const SOURCE_CANDIDATE_EXTENSIONS = ['.cts', '.mts', '.ts', '.tsx'];
const SOURCE_INDEX_EXTENSIONS = SOURCE_CANDIDATE_EXTENSIONS.map((extension) => `index${extension}`);

function resolveSourcePath(importer: string, source: string): string | undefined {
    const resolvedImportPath = path.resolve(path.dirname(importer), source);
    const extension = path.extname(resolvedImportPath);
    const candidates =
        extension === '.cjs' || extension === '.js' || extension === '.jsx' || extension === '.mjs'
            ? SOURCE_CANDIDATE_EXTENSIONS.map((candidateExtension) =>
                  `${resolvedImportPath.slice(0, -extension.length)}${candidateExtension}`,
              )
            : [
                  resolvedImportPath,
                  ...SOURCE_CANDIDATE_EXTENSIONS.map((candidateExtension) => `${resolvedImportPath}${candidateExtension}`),
                  ...SOURCE_INDEX_EXTENSIONS.map((indexFilename) => path.join(resolvedImportPath, indexFilename)),
              ];
    return candidates.find((candidate) => existsSync(candidate));
}

function runtimeForkPlugin(runtime: Runtime): TsdownPlugin {
    return {
        name: `runtime-fork-${runtime}`,
        resolveId(source, importer) {
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

function createConfig({
    entryName,
    runtime,
}: {
    entryName: string;
    runtime: Runtime;
}): UserConfig {
    return {
        clean: false,
        cwd: process.cwd(),
        define: {
            'process.env.BROWSER': JSON.stringify(false),
            'process.env.NODE_ENV': NODE_ENV_DEFINE_VALUE,
        },
        deps: {
            skipNodeModulesBundle: true,
        },
        dts: false,
        entry: {
            [entryName]: 'src/index.ts',
        },
        format: 'esm',
        hash: false,
        outDir: 'lib/esm',
        outExtensions: () => ({
            dts: '.d.ts',
            js: '.js',
        }),
        platform: runtime === 'react-native' ? 'neutral' : 'node',
        plugins: [runtimeForkPlugin(runtime)],
        sourcemap: true,
        tsconfig: 'tsconfig.json',
    };
}

function createDtsConfig(): UserConfig {
    return {
        clean: false,
        cwd: process.cwd(),
        dts: DTS_OPTIONS,
        entry: {
            index: 'src/index.ts',
        },
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
        entryName: 'index.native',
        runtime: 'react-native',
    }),
    createDtsConfig(),
]);
