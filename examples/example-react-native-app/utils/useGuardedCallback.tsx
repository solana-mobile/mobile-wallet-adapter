import React, {ReactNode, useCallback} from 'react';

export default function useGuardedCallback<TArgs extends Array<any>, TReturn>(
  cb: (...args: TArgs) => TReturn,
  setSnackbarProps: React.Dispatch<
    React.SetStateAction<{children: ReactNode} | null>
  >,
  dependencies?: Array<any>,
) {
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
