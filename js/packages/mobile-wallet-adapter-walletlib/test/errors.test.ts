import { describe, expect, it } from 'vitest';

import { SolanaMWAWalletLibError, SolanaMWAWalletLibErrorCode } from '../src/errors.js';

describe('SolanaMWAWalletLibError', () => {
    it('sets the expected name, message, and code', () => {
        const error = new SolanaMWAWalletLibError(
            SolanaMWAWalletLibErrorCode.ERROR_INTENT_DATA_NOT_FOUND,
            'Intent data not found',
        );

        expect(error).toBeInstanceOf(Error);
        expect(error.code).toBe(SolanaMWAWalletLibErrorCode.ERROR_INTENT_DATA_NOT_FOUND);
        expect(error.message).toBe('Intent data not found');
        expect(error.name).toBe('SolanaMWAWalletLibError');
    });
});
