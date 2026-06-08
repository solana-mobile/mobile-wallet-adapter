import { dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

import { FlatCompat } from '@eslint/eslintrc';
import js from '@eslint/js';
import simpleImportSort from 'eslint-plugin-simple-import-sort';

const compat = new FlatCompat({
    baseDirectory: dirname(fileURLToPath(import.meta.url)),
    recommendedConfig: js.configs.recommended,
});

const eslintConfig = [
    {
        ignores: ['.next/**', 'next-env.d.ts', 'node_modules/**', 'out/**'],
    },
    ...compat.extends('next/core-web-vitals', 'next/typescript'),
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
