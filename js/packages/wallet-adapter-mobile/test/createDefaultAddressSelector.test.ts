import { describe, expect, it } from 'vitest';

import createDefaultAddressSelector from '../src/createDefaultAddressSelector.js';

describe('createDefaultAddressSelector', () => {
    it('falls back to the first address when there are multiple options', async () => {
        const selector = createDefaultAddressSelector();

        await expect(selector.select(['first-address', 'second-address'])).resolves.toBe('first-address');
    });

    it('returns the only available address', async () => {
        const selector = createDefaultAddressSelector();

        await expect(selector.select(['only-address'])).resolves.toBe('only-address');
    });
});
