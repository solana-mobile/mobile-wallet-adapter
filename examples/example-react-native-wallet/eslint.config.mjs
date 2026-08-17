import reactNativeConfig from '@react-native/eslint-config/flat';
import simpleImportSort from 'eslint-plugin-simple-import-sort';

export default [
    {
        ignores: ['android/**/build/**', 'coverage/**', 'ios/Pods/**', 'node_modules/**'],
    },
    ...reactNativeConfig,
    {
        files: ['**/*.{cjs,cts,js,jsx,mjs,mts,ts,tsx}'],
        plugins: {
            'simple-import-sort': simpleImportSort,
        },
        rules: {
            '@react-native/no-deep-imports': 'off',
            'ft-flow/define-flow-type': 'off',
            'ft-flow/use-flow-type': 'off',
            'simple-import-sort/imports': 'error',
        },
    },
];
