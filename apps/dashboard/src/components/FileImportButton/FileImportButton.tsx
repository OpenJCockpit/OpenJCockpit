import { useRef, type ReactNode } from 'react';
import { ToolbarButton } from '../ToolbarButton/ToolbarButton';

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
      <ToolbarButton variant="small" onClick={() => inputRef.current?.click()}>
        {children}
      </ToolbarButton>
      <input
        ref={inputRef}
        type="file"
        accept={accept}
        className="workflow-design-hidden-input"
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
