import {useCallback, useContext} from 'react';

import {SnackbarContext} from '../components/SnackbarProvider';

export default function useGuardedCallback<TArgs extends Array<any>, TReturn>(
  cb: (...args: TArgs) => TReturn,
  dependencies?: Array<any>,
) {
  const setSnackbarProps = useContext(SnackbarContext);
  return useCallback(
    async (...args: TArgs) => {
      try {
        return await cb(...args);
      } catch (e: any) {
        setSnackbarProps({children: `${e.name}: ${e.message}`});
      }
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    dependencies || [],
  ) as (...args: TArgs) => Promise<Awaited<TReturn> | void>;
}
