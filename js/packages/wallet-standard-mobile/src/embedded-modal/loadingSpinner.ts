const modalHtml = `
<div class="mobile-wallet-adapter-embedded-loading-indicator" role="dialog" aria-modal="true" aria-labelledby="modal-title">
    <div data-modal-close style="position: absolute; width: 100%; height: 100%;"></div>
    <div class="mobile-wallet-adapter-embedded-loading-container">
        <div class="mobile-wallet-adapter-embedded-loading-animation"></div>
    </div>
</div>
`;

const css = `
.mobile-wallet-adapter-embedded-loading-indicator {
    display: flex; /* Use flexbox to center content */
    justify-content: center; /* Center horizontally */
    align-items: start; /* Center vertically */
    position: fixed; /* Stay in place */
    z-index: 1; /* Sit on top */
    left: 0;
    top: 0;
    width: 100%; /* Full width */
    height: 100%; /* Full height */
    background-color: rgba(0,0,0,0.4); /* Black w/ opacity */
    overflow-y: auto; /* enable scrolling */
}

.mobile-wallet-adapter-embedded-loading-container {
    display: flex;
    margin: auto;
}

.mobile-wallet-adapter-embedded-loading-animation {
    position: relative;
    left: -9999px;
    width: 10px;
    height: 10px;
    border-radius: 5px;
    background-color: var(--spinner-color);
    color: var(--spinner-color);
    box-shadow: 9984px 0 0 0 var(--spinner-color), 
                9999px 0 0 0 var(--spinner-color), 
                10014px 0 0 0 var(--spinner-color);
    animation: dot-typing 1.5s infinite linear;
}

@keyframes dot-typing {
    0% {
        box-shadow: 9984px 0 0 0 var(--spinner-color), 
                    9999px 0 0 0 var(--spinner-color), 
                    10014px 0 0 0 var(--spinner-color);
    }
    16.667% {
        box-shadow: 9984px -10px 0 0 var(--spinner-color), 
                    9999px 0 0 0 var(--spinner-color), 
                    10014px 0 0 0 var(--spinner-color);
    }
    33.333% {
        box-shadow: 9984px 0 0 0 var(--spinner-color), 
                    9999px 0 0 0 var(--spinner-color), 
                    10014px 0 0 0 var(--spinner-color);
    }
    50% {
        box-shadow: 9984px 0 0 0 var(--spinner-color), 
                    9999px -10px 0 0 var(--spinner-color), 
                    10014px 0 0 0 var(--spinner-color);
    }
    66.667% {
        box-shadow: 9984px 0 0 0 var(--spinner-color), 
                    9999px 0 0 0 var(--spinner-color), 
                    10014px 0 0 0 var(--spinner-color);
    }
    83.333% {
        box-shadow: 9984px 0 0 0 var(--spinner-color), 
                    9999px 0 0 0 var(--spinner-color), 
                    10014px -10px 0 0 var(--spinner-color);
    }
    100% {
        box-shadow: 9984px 0 0 0 var(--spinner-color), 
                    9999px 0 0 0 var(--spinner-color), 
                    10014px 0 0 0 var(--spinner-color);
    }
}
`;

interface LoadingSpinnerEventsListeners {
    /**
     * Listener that will be called when the modal is closed, either by the user or programmatically.
     *
     * @param event the event tied to the close event, or undefined if the modal was closed programmatically.
     */
    close(event?: Event): void;
}

type LoadingSpinnerEventsNames = keyof LoadingSpinnerEventsListeners;

export default class EmbeddedLoadingSpinner {
    #root: HTMLElement | null = null;
    #eventListeners: { [E in LoadingSpinnerEventsNames]?: LoadingSpinnerEventsListeners[E][] } = {};
    #listenersAttached = false;

    protected dom: ShadowRoot | null = null;

    constructor() {
        // Bind methods to ensure `this` context is correct
        this.init = this.init.bind(this);

        this.#root = document.getElementById('mobile-wallet-adapter-embedded-root-ui');
    }

    async init() {
        console.log('Injecting modal');
        this.#injectHTML();
    }

    open = () => {
        console.debug('Modal open');
        this.#attachEventListeners();
        if (this.#root) {
            this.#root.style.display = 'flex';
        }
    }

    close = (event: Event | undefined = undefined) => {
        console.debug('Modal close');
        this.#removeEventListeners();
        if (this.#root) {
            this.#root.style.display = 'none';
        }
        this.#eventListeners['close']?.forEach((listener) => listener(event));
    }

    addEventListener<E extends LoadingSpinnerEventsNames>(event: E, listener: LoadingSpinnerEventsListeners[E]) {
        this.#eventListeners[event]?.push(listener) || (this.#eventListeners[event] = [listener]);
        return (): void => this.removeEventListener(event, listener);
    }

    removeEventListener<E extends LoadingSpinnerEventsNames>(event: E, listener: (LoadingSpinnerEventsListeners[E])): void {
        this.#eventListeners[event] = this.#eventListeners[event]?.filter((existingListener) => listener !== existingListener);
    }

    #injectHTML() {
        // Check if the HTML has already been injected
        if (document.getElementById('mobile-wallet-adapter-embedded-root-ui')) {
            if (!this.#root) this.#root = document.getElementById('mobile-wallet-adapter-embedded-root-ui');
            return;
        }

        // Create a container for the modal
        this.#root = document.createElement('div');
        this.#root.id = 'mobile-wallet-adapter-embedded-root-ui';
        this.#root.innerHTML = modalHtml;
        this.#root.style.display = 'none';

        // Apply styles
        const styles = document.createElement('style');
        styles.id = 'mobile-wallet-adapter-embedded-modal-styles';
        styles.textContent = css;

        // Create a shadow DOM to encapsulate the modal
        const host = document.createElement('div');
        this.dom = host.attachShadow({ mode: 'closed' });

        // Pass the CSS variable to the Shadow DOM
        host.style.setProperty('--spinner-color', '#FFFFFF');

        this.dom.appendChild(styles);
        this.dom.appendChild(this.#root);

        // Append the shadow DOM host to the body
        document.body.appendChild(host);
    }

    #attachEventListeners() {
        if (!this.#root || this.#listenersAttached) return;

        const closers = [...this.#root.querySelectorAll('[data-modal-close]')];
        closers.forEach(closer => closer?.addEventListener('click', (event) => { this.close(event); }));

        window.addEventListener('load', this.close);
        document.addEventListener('keydown', this.#handleKeyDown);

        this.#listenersAttached = true;
    }

    #removeEventListeners() {
        if (!this.#listenersAttached) return;

        window.removeEventListener('load', this.close);
        document.removeEventListener('keydown', this.#handleKeyDown);

        if (!this.#root) return;
        const closers = [...this.#root.querySelectorAll('[data-modal-close]')];
        closers.forEach(closer => closer?.removeEventListener('click', this.close));

        this.#listenersAttached = false;
    }

    #handleKeyDown = (event: KeyboardEvent) => {
        if (event.key === 'Escape') this.close(event);
    }
}
