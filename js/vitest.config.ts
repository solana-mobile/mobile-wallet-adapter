import { defineConfig } from 'vitest/config';

export default defineConfig({
    test: {
        environmentOptions: {
            jsdom: {
                url: 'https://example.test',
            },
        },
        projects: ['packages/*'],
    },
});
