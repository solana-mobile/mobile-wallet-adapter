import { useSnackbar } from 'notistack';
import { useCallback } from 'react';

export default function useGuardedCallback<TArgs extends Array<any>, TReturn>(
    cb: (...args: TArgs) => TReturn,
    dependencies?: Array<any>,
) {
    const { enqueueSnackbar } = useSnackbar();
    return useCallback(
        async (...args: TArgs) => {
            try {
                return await cb(...args);
            } catch (e: any) {
                enqueueSnackbar(`${e.name}: ${e.message}`, { variant: 'error' });
            }
        },
        // eslint-disable-next-line react-hooks/exhaustive-deps
        [enqueueSnackbar, ...(dependencies || [])],
    ) as (...args: TArgs) => Promise<Awaited<TReturn> | void>;
}
