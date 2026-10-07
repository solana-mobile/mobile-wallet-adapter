import nextCoreWebVitals from 'eslint-config-next/core-web-vitals';
import nextTypescript from 'eslint-config-next/typescript';
import simpleImportSort from 'eslint-plugin-simple-import-sort';

const eslintConfig = [
    {
        ignores: ['.next/**', 'next-env.d.ts', 'node_modules/**', 'out/**'],
    },
    ...nextCoreWebVitals,
    ...nextTypescript,
    {
        files: ['**/*.{cjs,cts,js,jsx,mjs,mts,ts,tsx}'],
        plugins: {
            'simple-import-sort': simpleImportSort,
        },
        rules: {
            '@typescript-eslint/no-unused-vars': ['warn', { argsIgnorePattern: '^_' }],
            'simple-import-sort/imports': 'error',
        },
    },
    {
        files: ['next.config.js'],
        rules: {
            '@typescript-eslint/no-require-imports': 'off',
        },
    },
];

export default eslintConfig;
