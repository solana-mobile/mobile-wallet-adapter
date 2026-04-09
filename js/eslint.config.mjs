import { existsSync, lstatSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, resolve } from 'node:path';

import js from '@eslint/js';

const require = createRequire(import.meta.url);
const simpleImportSort = require('eslint-plugin-simple-import-sort');
const tseslint = require('@typescript-eslint/eslint-plugin');
const requireExtensions = {
    rules: {
        'require-extensions': createRequireExtensionsRule((context, node, path) => {
            if (!existsSync(path)) {
                let fix;
                if (!node.source.value.includes('?')) {
                    fix = (fixer) => fixer.replaceText(node.source, `'${node.source.value}.js'`);
                }

                context.report({
                    node,
                    message: 'Relative imports and exports must end with .js',
                    fix,
                });
            }
        }),
        'require-index': createRequireExtensionsRule((context, node, path) => {
            if (existsSync(path) && lstatSync(path).isDirectory()) {
                context.report({
                    node,
                    message: 'Directory paths must end with index.js',
                    fix(fixer) {
                        if (!node.source.value.includes('?')) {
                            return fixer.replaceText(node.source, `'${node.source.value}/index.js'`);
                        }

                        const [path, query] = node.source.value.split('?');
                        return fixer.replaceText(node.source, `'${path}/index.js?${query}'`);
                    },
                });
            }
        }),
    },
};

export default [
    {
        ignores: ['.turbo/**', 'node_modules/**', 'packages/*/android/build/**', 'packages/*/lib/**'],
    },
    js.configs.recommended,
    ...tseslint.configs['flat/recommended'],
    {
        files: ['**/*.{cjs,cts,js,mjs,mts,ts,tsx}'],
        plugins: {
            'require-extensions': requireExtensions,
            'simple-import-sort': simpleImportSort,
        },
        rules: {
            'require-extensions/require-extensions': 'error',
            'require-extensions/require-index': 'error',
            'simple-import-sort/imports': 'error',
        },
    },
    {
        files: ['**/*.{cts,mts,ts,tsx}'],
        rules: {
            '@typescript-eslint/no-unused-expressions': [
                'error',
                { allowShortCircuit: true, allowTaggedTemplates: false, allowTernary: false },
            ],
            '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_' }],
        },
    },
];

function createRequireExtensionsRule(check) {
    return {
        meta: {
            fixable: 'code',
            schema: [],
        },
        create(context) {
            function rule(node) {
                const source = node.source;
                if (!source) return;

                const value = source.value.replace(/\?.*$/, '');
                if (!value || !value.startsWith('.') || value.endsWith('.js')) return;

                check(context, node, resolve(dirname(context.filename), value));
            }

            return {
                DeclareExportAllDeclaration: rule,
                DeclareExportDeclaration: rule,
                ExportAllDeclaration: rule,
                ExportNamedDeclaration: rule,
                ImportDeclaration: rule,
            };
        },
    };
}
