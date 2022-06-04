export default function getStringWithURLUnsafeCharactersReplaced(unsafeBase64EncodedString: string): string {
    return unsafeBase64EncodedString.replace(
        /[/+=]/g,
        (m) =>
            ({
                '/': '_',
                '+': '-',
                '=': '.',
            }[m] as string),
    );
}
