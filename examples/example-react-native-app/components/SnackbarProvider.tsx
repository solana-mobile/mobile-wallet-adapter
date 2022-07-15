import React, {createContext, ReactNode, useState} from 'react';
import {Portal, Snackbar} from 'react-native-paper';

type Props = Readonly<{children: ReactNode}>;

type SnackbarProps = Partial<React.ComponentProps<typeof Snackbar>> & {
  children: ReactNode;
};

export const SnackbarContext = /*#__PURE__*/ createContext<
  React.Dispatch<React.SetStateAction<SnackbarProps | null>>
>(() => {});

export default function SnackbarProvider({children}: Props) {
  const [snackbarProps, setSnackbarProps] = useState<SnackbarProps | null>(
    null,
  );
  return (
    <SnackbarContext.Provider value={setSnackbarProps}>
      {children}
      <Portal>
        <Snackbar
          children={null}
          onDismiss={() => {
            setSnackbarProps(null);
          }}
          visible={snackbarProps != null}
          {...snackbarProps}
        />
      </Portal>
    </SnackbarContext.Provider>
  );
}
