import { useEffect, useId, useRef, useState } from 'react';
import type { KeyboardEvent } from 'react';
import { cx } from '../cx';
import styles from './Select.module.scss';

export interface SelectOption {
  value: string;
  label: string;
  /** Language tag of the label, for options that are written in their own language. */
  lang?: string;
}

interface Props {
  value: string;
  options: SelectOption[];
  onChange: (value: string) => void;
  /** Accessible name and hover title. Omit when an enclosing or `htmlFor` <label> names the control. */
  label?: string;
  id?: string;
  disabled?: boolean;
  /** Stretches the control to the full width of its container, as a form input does. */
  block?: boolean;
  className?: string;
  'aria-describedby'?: string;
  'data-testid'?: string;
}

/** Compact cyan dropdown (button + listbox) matching the dashboard look; replaces the native `<select>` popup. */
export function Select({
  value,
  options,
  onChange,
  label,
  id,
  disabled,
  block,
  className,
  ...rest
}: Props) {
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(0);
  const rootRef = useRef<HTMLDivElement>(null);
  const listId = useId();
  const selectedIndex = options.findIndex((o) => o.value === value);
  // Like a native <select>, show the first option when the value matches none.
  const selected = options[selectedIndex] ?? options[0];

  useEffect(() => {
    if (!open) return;
    const onPointerDown = (e: PointerEvent) => {
      if (!rootRef.current?.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('pointerdown', onPointerDown);
    return () => document.removeEventListener('pointerdown', onPointerDown);
  }, [open]);

  const openList = () => {
    setActive(Math.max(selectedIndex, 0));
    setOpen(true);
  };

  const choose = (index: number) => {
    const option = options[index];
    setOpen(false);
    if (option && option.value !== value) onChange(option.value);
  };

  const onKeyDown = (e: KeyboardEvent<HTMLButtonElement>) => {
    const last = options.length - 1;
    switch (e.key) {
      case 'ArrowDown':
      case 'ArrowUp': {
        e.preventDefault();
        if (!open) return openList();
        setActive((i) => (e.key === 'ArrowDown' ? Math.min(i + 1, last) : Math.max(i - 1, 0)));
        break;
      }
      case 'Home':
      case 'End':
        if (!open) return;
        e.preventDefault();
        setActive(e.key === 'Home' ? 0 : last);
        break;
      case 'Enter':
      case ' ':
        e.preventDefault();
        if (open) choose(active);
        else openList();
        break;
      case 'Escape':
        if (open) {
          e.preventDefault();
          setOpen(false);
        }
        break;
      case 'Tab':
        setOpen(false);
        break;
    }
  };

  return (
    <div ref={rootRef} className={cx(styles.select, block && styles.block, className)}>
      <button
        type="button"
        id={id}
        className={styles.trigger}
        disabled={disabled}
        role="combobox"
        aria-label={label}
        title={label}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={listId}
        aria-describedby={rest['aria-describedby']}
        aria-activedescendant={open ? `${listId}-${active}` : undefined}
        onClick={() => (open ? setOpen(false) : openList())}
        onKeyDown={onKeyDown}
        data-testid={rest['data-testid']}
        data-value={value}
      >
        <span className={styles.value} lang={selected?.lang}>
          {selected?.label ?? ''}
        </span>
        <span className={styles.chevron} aria-hidden="true" />
      </button>
      {open && !disabled && (
        <ul id={listId} role="listbox" aria-label={label} className={styles.list}>
          {options.map((o, i) => (
            // Keyboard handling lives on the combobox trigger, which keeps focus (aria-activedescendant).
            // eslint-disable-next-line jsx-a11y/click-events-have-key-events -- keys are handled on the trigger
            <li
              key={o.value}
              id={`${listId}-${i}`}
              role="option"
              lang={o.lang}
              aria-selected={o.value === value}
              className={cx(styles.option, i === active && styles.active)}
              onPointerEnter={() => setActive(i)}
              onClick={() => choose(i)}
            >
              {o.label}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
