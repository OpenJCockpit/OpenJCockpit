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
}

interface ErrorBoundaryState {
  error: Error | null;
}

function keysChanged(a: readonly unknown[] = [], b: readonly unknown[] = []): boolean {
  return a.length !== b.length || a.some((key, i) => !Object.is(key, b[i]));
}

/** Catches render errors below it and shows a reload fallback instead of a blank page. */
export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { error: null };

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Unhandled render error', error, info.componentStack);
  }

  componentDidUpdate(prev: ErrorBoundaryProps) {
    if (this.state.error && keysChanged(prev.resetKeys, this.props.resetKeys)) {
      this.setState({ error: null });
    }
  }

  render() {
    if (!this.state.error) return this.props.children;
    return (
      <StatusMessage variant="loading" role="alert" data-testid="error-boundary">
        <div>
          <p>{this.props.message ?? 'Something went wrong.'}</p>
          <Button variant="primary" onClick={() => window.location.reload()}>
            Reload
          </Button>
        </div>
      </StatusMessage>
    );
  }
}
