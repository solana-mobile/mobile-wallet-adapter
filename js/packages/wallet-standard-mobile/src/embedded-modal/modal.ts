import { createEmbeddedModalElement, type EmbeddedModalElements, type EmbeddedModalTag } from './elements.js';

interface ModalEventsListeners {
    /**
     * Listener that will be called when the modal is closed, either by the user or programmatically.
     *
     * @param event the event tied to the close event, or undefined if the modal was closed programmatically.
     */
    close(event?: Event): void;
}

type ModalEventsNames = keyof ModalEventsListeners;

/** Shows one of the embedded modal elements and reports when it closes. */
export default abstract class EmbeddedModal<Tag extends EmbeddedModalTag = EmbeddedModalTag> {
    #eventListeners: { [E in ModalEventsNames]?: ModalEventsListeners[E][] } = {};

    protected abstract readonly tag: Tag;
    protected element: EmbeddedModalElements[Tag] | null = null;

    init() {
        if (this.element) return;
        this.element = createEmbeddedModalElement(this.tag);
        this.element.onDismiss = this.close;
    }

    open = () => {
        if (this.element) document.body.appendChild(this.element);
    };

    close = (event: Event | undefined = undefined) => {
        this.element?.remove();
        this.#eventListeners['close']?.forEach((listener) => listener(event));
    };

    addEventListener<E extends ModalEventsNames>(event: E, listener: ModalEventsListeners[E]) {
        this.#eventListeners[event]?.push(listener) || (this.#eventListeners[event] = [listener]);
        return (): void => this.removeEventListener(event, listener);
    }

    removeEventListener<E extends ModalEventsNames>(event: E, listener: ModalEventsListeners[E]): void {
        this.#eventListeners[event] = this.#eventListeners[event]?.filter(
            (existingListener) => listener !== existingListener,
        );
    }
}
