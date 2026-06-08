module.exports = {
    extends: '@react-native',
    plugins: ['simple-import-sort'],
    root: true,
    rules: {
        '@react-native/no-deep-imports': 'off',
        'simple-import-sort/imports': 'error',
    },
};
