import { defineConfig } from 'vitest/config';

export default defineConfig({
    test: {
        coverage: {
            include: ['packages/*/src/**/*.{cjs,cts,js,jsx,mjs,mts,ts,tsx}'],
            reportOnFailure: true,
            reporter: ['html', 'text'],
            skipFull: true,
        },
        environmentOptions: {
            jsdom: {
                url: 'https://example.test',
            },
        },
        projects: ['packages/*'],
    },
});
