import { useRef, type ReactNode } from 'react';
import { Button } from '../Button/Button';
import styles from './FileImportButton.module.scss';

interface FileImportButtonProps {
  children: ReactNode;
  accept: string;
  onFile: (file: File) => void;
  /** Accessible name of the hidden file input. */
  inputLabel: string;
}

/** Button that opens a hidden file input and hands the chosen file to `onFile`. */
export function FileImportButton({ children, accept, onFile, inputLabel }: FileImportButtonProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  return (
    <>
      <Button variant="secondary" onClick={() => inputRef.current?.click()}>
        {children}
      </Button>
      <input
        ref={inputRef}
        type="file"
        accept={accept}
        className={styles.fileImportInput}
        aria-label={inputLabel}
        onChange={(e) => {
          const file = e.target.files?.[0];
          if (file) onFile(file);
          e.target.value = '';
        }}
      />
    </>
  );
}
