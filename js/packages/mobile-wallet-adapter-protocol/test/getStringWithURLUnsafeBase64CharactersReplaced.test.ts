import { describe, expect, it } from 'vitest';

import getStringWithURLUnsafeBase64CharactersReplaced from '../src/getStringWithURLUnsafeBase64CharactersReplaced.js';

describe('getStringWithURLUnsafeBase64CharactersReplaced', () => {
    it('replaces URL-unsafe base64 characters', () => {
        expect(getStringWithURLUnsafeBase64CharactersReplaced('+/=')).toBe('-_.');
    });

    it('preserves characters that are already URL-safe', () => {
        expect(getStringWithURLUnsafeBase64CharactersReplaced('abc123')).toBe('abc123');
    });
});
