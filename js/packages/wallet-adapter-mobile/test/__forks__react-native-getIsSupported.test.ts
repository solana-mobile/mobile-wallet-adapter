import { afterEach, describe, expect, it, vi } from 'vitest';

const { platform } = vi.hoisted(() => ({
    platform: { OS: 'android' },
}));

vi.mock('react-native', () => ({
    Platform: platform,
}));

import getIsSupported from '../src/__forks__/react-native/getIsSupported.js';

afterEach(() => {
    platform.OS = 'android';
});

describe('react-native getIsSupported fork', () => {
    it('returns true on Android', () => {
        platform.OS = 'android';

        expect(getIsSupported()).toBe(true);
    });

    it('returns false on non-Android platforms', () => {
        platform.OS = 'ios';

        expect(getIsSupported()).toBe(false);
    });
});
