export const css = `
.mwa-modal-container {
    background: #ffffff;
    padding: 20px;
    border-radius: 24px;
    display: flex;
    flex-direction: column;       
    flex-wrap: wrap;
    font-family: Arial, Verdana, sans-serif;
}

.mwa-modal-container> div:nth-child(2) {
    display: flex; 
    /*border: 1px solid blue;*/
    margin-top: 40px;
    padding: 10px;
}

.mwa-modal-container > div:nth-child(2) > div:first-child {
    display: flex;
    flex-direction: column;
    flex: 2;
    /*border: 1px solid orange;*/
    margin-right: 30px;
}

.mwa-modal-container > div:nth-child(2) > div:nth-child(2) {
    display: flex;
    flex-direction: column;
    flex: 1;
    /*border: 1px solid orange;*/
    margin-left: auto;
}

.mwa-modal-container > div:nth-child(4) {
    display: flex;
    padding: 10px;
    /*border: 1px solid red;*/
}

/*.mwa-modal-container > div:nth-child(4) > div:first-child {*/
/*    border: 1px solid magenta;*/
/*}*/

/*.mwa-modal-container > div:nth-child(4) > div:nth-child(2) {*/
/*    border: 1px solid pink;*/
/*}*/

.mwa-modal-close {
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

.mwa-modal-close:focus-visible {
    outline-color: red;
}

.mwa-modal-close svg {
    fill: #546266;
    transition: fill 200ms ease 0s;
}

.mwa-modal-close:hover svg {
    fill: #fff;
}

.mwa-modal-title {
    margin-top: 40px;
    color: #000000;
    font-size: 40px;
    font-weight: bold;
}

.mwa-modal-qr-label {
    text-align: right;
    color: #000000;
}

.mwa-modal-qr-code-container {
    /*border: 1px solid red;*/
}

.mwa-modal-divider {
    margin-top: 20px;
    padding-left: 10px;
    padding-right: 10px;
    /*margin-bottom: 10px;*/
}

.mwa-modal-divider hr {
    border-top: 1px solid #D9DEDE;
}

.mwa-modal-subtitle {
    margin: auto;
    padding: 20px;
    color: #6E8286;
}

.mwa-modal-progress-badge {
    display: flex;
    background: #F7F8F8;
    height: 56px;
    min-width: 200px;
    margin: auto;
    padding-left: 20px;
    padding-right: 20px;
    border-radius: 24px;
    color: #A8B6B8;
    align-items: center;
}

.mwa-modal-progress-badge > div:first-child {
    margin-left: auto;
    margin-right: 20px;
}

.mwa-modal-progress-badge > div:nth-child(2) {
    margin-right: auto;
}

/* Spinner */
@keyframes spinLeft {
    0% {
        transform: rotate(20deg);
    }
    50% {
        transform: rotate(160deg);
    }
    100% {
        transform: rotate(20deg);
    }
}
@keyframes spinRight {
    0% {
        transform: rotate(160deg);
    }
    50% {
        transform: rotate(20deg);
    }
    100% {
        transform: rotate(160deg);
    }
}
@keyframes spin {
    0% {
        transform: rotate(0deg);
    }
    100% {
        transform: rotate(2520deg);
    }
}

.spinner {
    position: relative;
    width: 24px;
    height: 24px;
    margin: auto;
    animation: spin 10s linear infinite;
}
.spinner::before {
    content: "";
    position: absolute;
    top: 0;
    bottom: 0;
    left: 0;
    right: 0;
    /*border: 2px solid #D4D7DC;*/
    /*border-radius: 12px;*/
}
.right, .rightWrapper, .left, .leftWrapper {
    position: absolute;
    top: 0;
    overflow: hidden;
    width: 12px;
    height: 24px;
}
.left, .leftWrapper {
    left: 0;
}
.right {
    left: -12px;
}
.rightWrapper {
    right: 0;
}
.circle {
    border: 2px solid #A8B6B8;
    width: 20px; /* 24px - 2*2px border */
    height: 20px; /* 24px - 2*2px border */
    border-radius: 12px;
}
.left {
    transform-origin: 100% 50%;
    animation: spinLeft 2.5s cubic-bezier(.2,0,.8,1) infinite;
}
.right {
    transform-origin: 100% 50%;
    animation: spinRight 2.5s cubic-bezier(.2,0,.8,1) infinite;
}
`;
