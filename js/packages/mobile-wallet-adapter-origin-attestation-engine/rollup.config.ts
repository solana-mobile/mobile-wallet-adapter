import { RollupOptions } from 'rollup';
import ts from 'rollup-plugin-ts';

export default [
    {
        input: 'src/index.ts',
        output: {
            dir: 'lib',
            format: 'esm',
        },
        plugins: [ts()],
    },
    {
        input: 'src/startAttestationEngine.ts',
        output: {
            dir: 'lib',
            format: 'esm',
        },
        plugins: [ts()],
    },
] as RollupOptions[];
