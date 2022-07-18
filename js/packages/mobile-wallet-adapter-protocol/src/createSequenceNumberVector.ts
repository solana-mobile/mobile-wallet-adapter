export const SEQUENCE_NUMBER_BYTES = 4;

export default function createSequenceNumberVector(sequenceNumber: number): Uint8Array {
    if (sequenceNumber >= 4294967296) {
        throw new Error('Outbound sequence number overflow. The maximum sequence number is 32-bytes.');
    }
    const byteArray = new ArrayBuffer(SEQUENCE_NUMBER_BYTES);
    const view = new DataView(byteArray);
    view.setUint32(0, sequenceNumber, /* littleEndian */ false);
    return new Uint8Array(byteArray);
}
