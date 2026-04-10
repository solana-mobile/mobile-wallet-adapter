import { fromUint8Array as jsBase64FromUint8Array, toUint8Array as jsBase64ToUint8Array } from 'js-base64';
import { describe, expect, it } from 'vitest';

import { fromUint8Array, toUint8Array } from '../src/__forks__/react-native/base64Utils.js';

describe('react-native base64Utils fork', () => {
    it('re-exports the js-base64 helpers', () => {
        expect(fromUint8Array).toBe(jsBase64FromUint8Array);
        expect(toUint8Array).toBe(jsBase64ToUint8Array);
    });
});
