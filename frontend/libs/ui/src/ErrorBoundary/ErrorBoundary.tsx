import { Component } from 'react';
import type { ErrorInfo, ReactNode } from 'react';
import { Button } from '../Button/Button';
import { StatusMessage } from '../StatusMessage/StatusMessage';

interface ErrorBoundaryProps {
  children: ReactNode;
  /** Clears the error when any entry changes, e.g. the current view. */
  resetKeys?: readonly unknown[];
  /** Message shown above the reload action. */
  message?: string;
  /** Label of the reload button. */
  reloadLabel?: string;
}

interface ErrorBoundaryState {
  error: Error | null;
}

function keysChanged(a: readonly unknown[] = [], b: readonly unknown[] = []): boolean {
  return a.length !== b.length || a.some((key, i) => !Object.is(key, b[i]));
}

/**
 * Catches render errors below it and shows a reload fallback instead of a blank page.
 * The outermost boundary sits outside the i18n provider so a broken i18n setup still shows an error, so its
 * copy defaults to English. Boundaries inside the provider pass translated `message` and `reloadLabel`.
 */
/* eslint-disable i18next/no-literal-string -- outside the i18n provider, see above */
export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  override state: ErrorBoundaryState = { error: null };

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { error };
  }

  override componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Unhandled render error', error, info.componentStack);
  }

  override componentDidUpdate(prev: ErrorBoundaryProps) {
    if (this.state.error && keysChanged(prev.resetKeys, this.props.resetKeys)) {
      this.setState({ error: null });
    }
  }

  override render() {
    if (!this.state.error) return this.props.children;
    return (
      <StatusMessage variant="page" role="alert" data-testid="error-boundary">
        <p>{this.props.message ?? 'Something went wrong.'}</p>
        <Button variant="primary" onClick={() => window.location.reload()}>
          {this.props.reloadLabel ?? 'Reload'}
        </Button>
      </StatusMessage>
    );
  }
}
