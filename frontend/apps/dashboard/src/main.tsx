import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router';
import { QueryClientProvider } from '@tanstack/react-query';
import './styles/index.scss';
import { ErrorBoundary } from '@openjcockpit/ui';
import App from './App';
import { createQueryClient } from './queryClient';

const queryClient = createQueryClient();

// StrictMode is not enabled here. The original Arwes-related justification no longer
// applies (Arwes has been removed from this app), but this app's existing effects have
// never been verified under StrictMode double-invocation, and this pattern-only change
// does not cover that verification. Enabling StrictMode is a deliberate, separate
// follow-up, not implied by any remaining technical constraint.
createRoot(document.getElementById('root') as HTMLElement).render(
  <ErrorBoundary message="The application hit an unexpected error.">
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </QueryClientProvider>
  </ErrorBoundary>,
);
