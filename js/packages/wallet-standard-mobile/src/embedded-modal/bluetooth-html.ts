export const BluetoothHtml = `
    <div class="mobile-wallet-adapter-embedded-modal-content">
    <button id="mobile-wallet-adapter-embedded-modal-close" class="mobile-wallet-adapter-embedded-modal-close">
        <svg width="14" height="14">
        <path d="M14 12.461 8.3 6.772l5.234-5.233L12.006 0 6.772 5.234 1.54 0 0 1.539l5.234 5.233L0 12.006l1.539 1.528L6.772 8.3l5.69 5.7L14 12.461z" />
        </svg>
    </button>
    <h1><b>Jupiter</b> wants to connect</h1>
    <p class="mobile-wallet-adapter-embedded-modal-subtitle">Connect to your mobile wallet app through Bluetooth.</p>
    <div class="mobile-wallet-adapter-embedded-modal-connection-status-container">
        <div id="status-not-connected" class="connection-status">
        <svg class="bluetooth-icon" width="24" height="24" viewBox="0 0 24 24">
            <path fill="#a0a0a0" d="M14.24 12.01l2.32 2.32c.28-.72.44-1.51.44-2.33 0-.82-.16-1.59-.43-2.31l-2.33 2.32zm5.29-5.3l-1.26 1.26c.63 1.21.98 2.57.98 4.02s-.36 2.82-.98 4.02l1.2 1.2c.97-1.54 1.54-3.36 1.54-5.31-.01-1.89-.55-3.67-1.48-5.19zm-3.82 1L10 2H9v7.59L4.41 5 3 6.41 8.59 12 3 17.59 4.41 19 9 14.41V22h1l5.71-5.71-4.3-4.29 4.3-4.29zM11 5.83l1.88 1.88L11 9.59V5.83zm1.88 10.46L11 18.17v-3.76l1.88 1.88z"/>
        </svg>
        <p>Not connected</p>
        </div>
        <div id="status-connecting" class="connection-status" style="display:none;">
        <div class="spinner"></div>
        <p>Connecting...</p>
        </div>
        <div id="status-connected" class="connection-status" style="display:none;">
        <svg class="checkmark-icon" width="24" height="24" viewBox="0 0 24 24">
            <path fill="#4CAF50" d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"/>
        </svg>
        <p>Connected</p>
        </div>
    </div>
    <div class="button-group">
        <button id="cancel-btn" class="cancel-btn">Cancel</button>
        <button id="connect-btn" class="connect-btn">Connect</button>
    </div>
    </div>
`;
