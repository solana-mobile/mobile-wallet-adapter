import type { EmbeddedModalElements, EmbeddedModalTag } from '../../elements.js';

// React Native has no DOM, and importing Lit there throws at module load.
export function createEmbeddedModalElement<Tag extends EmbeddedModalTag>(_tag: Tag): EmbeddedModalElements[Tag] {
    throw new Error('Embedded modals require a DOM and are not available in React Native');
}
