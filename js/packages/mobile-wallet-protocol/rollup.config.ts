import commonJsPlugin from '@rollup/plugin-commonjs';
import nodeResolve from '@rollup/plugin-node-resolve';
import replace from '@rollup/plugin-replace';
import type { RollupOptions } from 'rollup';
import ts from 'rollup-plugin-ts';
import externals from 'rollup-plugin-node-externals';

function createConfig({
    bundleName,
    format,
    isBrowser,
}: {
    bundleName: string;
    format: 'cjs' | 'esm';
    isBrowser: boolean;
}): RollupOptions {
    return {
        input: 'src/index.ts',
        output: {
            file: 'lib/' + format + '/' + bundleName,
            format,
            sourcemap: true,
        },
        plugins: [
            externals(),
            nodeResolve({ browser: isBrowser, extensions: ['.ts'], preferBuiltins: !isBrowser }),
            ts({ tsconfig: format === 'cjs' ? 'tsconfig.cjs.json' : 'tsconfig.json' }),
            commonJsPlugin({ exclude: 'node_modules', extensions: ['.js', '.ts'] }),
            replace({
                preventAssignment: true,
                values: {
                    'process.env.BROWSER': JSON.stringify(isBrowser),
                    'process.env.NODE_ENV': JSON.stringify(process.env.NODE_ENV),
                },
            }),
        ],
    };
}

const config: RollupOptions[] = [
    createConfig({ bundleName: 'index.js', format: 'cjs', isBrowser: false }),
    createConfig({ bundleName: 'index.browser.js', format: 'cjs', isBrowser: true }),
    createConfig({ bundleName: 'index.mjs', format: 'esm', isBrowser: false }),
    createConfig({ bundleName: 'index.browser.mjs', format: 'esm', isBrowser: true }),
];

export default config;
