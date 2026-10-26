import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { FileImportButton } from './FileImportButton';

describe('FileImportButton', () => {
  it('passes the chosen file to onFile', () => {
    const onFile = vi.fn();
    render(
      <FileImportButton accept=".json" inputLabel="Import" onFile={onFile}>
        Import JSON
      </FileImportButton>,
    );
    const file = new File(['{}'], 'a.json', { type: 'application/json' });
    fireEvent.change(screen.getByLabelText('Import'), { target: { files: [file] } });
    expect(onFile).toHaveBeenCalledWith(file);
  });

  it('opens the hidden file input when the button is clicked', () => {
    render(
      <FileImportButton accept=".json" inputLabel="Import" onFile={vi.fn()}>
        Import JSON
      </FileImportButton>,
    );
    const input = screen.getByLabelText('Import');
    const click = vi.spyOn(input, 'click');
    fireEvent.click(screen.getByRole('button', { name: 'Import JSON' }));
    expect(click).toHaveBeenCalledTimes(1);
  });

  it('ignores an empty selection', () => {
    const onFile = vi.fn();
    render(
      <FileImportButton accept=".json" inputLabel="Import" onFile={onFile}>
        Import JSON
      </FileImportButton>,
    );
    fireEvent.change(screen.getByLabelText('Import'), { target: { files: [] } });
    expect(onFile).not.toHaveBeenCalled();
  });
});
