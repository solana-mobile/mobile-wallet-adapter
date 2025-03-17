const modalHtml = `
<div class="mobile-wallet-adapter-embedded-modal-container">
    <div data-modal-close style="position: absolute; width: 100%; height: 100%;"></div>
	<div class="mobile-wallet-adapter-embedded-modal-card">
		<div>
			<button data-modal-close class="mobile-wallet-adapter-embedded-modal-close">
				<svg width="14" height="14">
					<path d="M 6.7125,8.3036995 1.9082,13.108199 c -0.2113,0.2112 -0.4765,0.3168 -0.7957,0.3168 -0.3192,0 -0.5844,-0.1056 -0.7958,-0.3168 C 0.1056,12.896899 0,12.631699 0,12.312499 c 0,-0.3192 0.1056,-0.5844 0.3167,-0.7958 L 5.1212,6.7124995 0.3167,1.9082 C 0.1056,1.6969 0,1.4317 0,1.1125 0,0.7933 0.1056,0.5281 0.3167,0.3167 0.5281,0.1056 0.7933,0 1.1125,0 1.4317,0 1.6969,0.1056 1.9082,0.3167 L 6.7125,5.1212 11.5167,0.3167 C 11.7281,0.1056 11.9933,0 12.3125,0 c 0.3192,0 0.5844,0.1056 0.7957,0.3167 0.2112,0.2114 0.3168,0.4766 0.3168,0.7958 0,0.3192 -0.1056,0.5844 -0.3168,0.7957 L 8.3037001,6.7124995 13.1082,11.516699 c 0.2112,0.2114 0.3168,0.4766 0.3168,0.7958 0,0.3192 -0.1056,0.5844 -0.3168,0.7957 -0.2113,0.2112 -0.4765,0.3168 -0.7957,0.3168 -0.3192,0 -0.5844,-0.1056 -0.7958,-0.3168 z" />
				</svg>
			</button>
		</div>
		<div class="mobile-wallet-adapter-embedded-modal-content"></div>
	</div>
</div>
`;

const css = `
.mobile-wallet-adapter-embedded-modal-container {
    display: flex; /* Use flexbox to center content */
    justify-content: center; /* Center horizontally */
    align-items: center; /* Center vertically */
    position: fixed; /* Stay in place */
    z-index: 1; /* Sit on top */
    left: 0;
    top: 0;
    width: 100%; /* Full width */
    height: 100%; /* Full height */
    background-color: rgba(0,0,0,0.4); /* Black w/ opacity */
    overflow-y: auto; /* enable scrolling */
}

.mobile-wallet-adapter-embedded-modal-card {
    display: flex;
    flex-direction: column;
    margin: auto 20px;
    max-width: 780px;
    padding: 20px;
    border-radius: 24px;
    background: #ffffff;
    font-family: "Inter Tight", "PT Sans", Calibri, sans-serif;
    transform: translateY(-200%);
    animation: slide-in 0.5s forwards;
}

@keyframes slide-in {
    100% { transform: translateY(0%); }
}

.mobile-wallet-adapter-embedded-modal-close {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 32px;
    height: 32px;
    cursor: pointer;
    background: #e4e9e9;
    border: none;
    border-radius: 50%;
}

.mobile-wallet-adapter-embedded-modal-close:focus-visible {
    outline-color: red;
}

.mobile-wallet-adapter-embedded-modal-close svg {
    fill: #546266;
    transition: fill 200ms ease 0s;
}

.mobile-wallet-adapter-embedded-modal-close:hover svg {
    fill: #fff;
}
`;

const fonts = `
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Inter+Tight:ital,wght@0,100..900;1,100..900&display=swap" rel="stylesheet">
`;

export default abstract class EmbeddedModal {
    private _root: HTMLElement | null = null;

    protected dom: ShadowRoot | null = null;

    protected abstract contentStyles: string;
    protected abstract contentHtml: string;

    private _onCloseListeners: (() => void)[] = [];

    constructor() {
        // Bind methods to ensure `this` context is correct
        this.init = this.init.bind(this);
        this.injectHTML = this.injectHTML.bind(this);
        this.open = this.open.bind(this);
        this.close = this.close.bind(this);
        this._handleKeyDown = this._handleKeyDown.bind(this);

        this._root = document.getElementById('mobile-wallet-adapter-embedded-root-ui');
    }

    async init() {
        console.log('Injecting modal');
        this.injectHTML();
    }

    addOnCloseListener(listener: () => void) {
        this._onCloseListeners.push(listener);
    }

    private injectHTML() {
        // Check if the HTML has already been injected
        if (document.getElementById('mobile-wallet-adapter-embedded-root-ui')) {
            if (!this._root) this._root = document.getElementById('mobile-wallet-adapter-embedded-root-ui');
            return;
        }

        // Create a container for the modal
        this._root = document.createElement('div');
        this._root.id = 'mobile-wallet-adapter-embedded-root-ui';
        this._root.innerHTML = modalHtml;
        this._root.style.display = 'none';

        // Add modal content
        const content = this._root.querySelector('.mobile-wallet-adapter-embedded-modal-content');
        if (content) content.innerHTML = this.contentHtml;

        // Apply styles
        const styles = document.createElement('style');
        styles.id = 'mobile-wallet-adapter-embedded-modal-styles';
        styles.textContent = css + this.contentStyles;

        // Create a shadow DOM to encapsulate the modal
        const host = document.createElement('div');
        host.innerHTML = fonts;
        this.dom = host.attachShadow({ mode: 'closed' });
        this.dom.appendChild(styles);
        this.dom.appendChild(this._root);

        // Append the shadow DOM host to the body
        document.body.appendChild(host);
    }

    private attachEventListeners() {
        if (!this._root) return;

        const closers = [...this._root.querySelectorAll('[data-modal-close]')];
        closers.forEach(closer => closer?.addEventListener('click', this.close));

        window.addEventListener('load', this.close);
        document.addEventListener('keydown', this._handleKeyDown);
    }

    private removeEventListeners() {
        window.removeEventListener('load', this.close);
        document.removeEventListener('keydown', this._handleKeyDown);

        if (!this._root) return;
        const closers = [...this._root.querySelectorAll('[data-modal-close]')];
        closers.forEach(closer => closer?.removeEventListener('click', this.close));
    }

    open() {
        console.debug('Modal open');
        this.attachEventListeners();
        if (this._root) {
            this._root.style.display = 'flex';
        }
    }

    close() {
        console.debug('Modal close');
        this.removeEventListeners();
        if (this._root) {
            this._root.style.display = 'none';
        }
        this._onCloseListeners.forEach(listener => listener());
    }

    private _handleKeyDown(event: KeyboardEvent) {
        if (event.key === 'Escape') this.close();
    }
}
