export function getStringWithURLUnsafeBase64CharactersReplaced(unsafeBase64EncodedString: string): string {
    return unsafeBase64EncodedString.replace(
        /[/+=]/g,
        (m) =>
            ({
                '/': '_',
                '+': '-',
                '=': '.',
            })[m] as string,
    );
}

/**
 * @deprecated Use {@link getStringWithURLUnsafeBase64CharactersReplaced} instead.
 */
export default getStringWithURLUnsafeBase64CharactersReplaced;
