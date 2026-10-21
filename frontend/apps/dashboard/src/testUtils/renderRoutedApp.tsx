import { render } from '@testing-library/react';
import { QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router';
import App from '../App';
import { createQueryClient } from '../queryClient';

/** Renders the full App at `entry`; router and query cache are per-render so nothing leaks between tests. */
export function renderRoutedApp(entry = '/') {
  return render(
    <QueryClientProvider client={createQueryClient({ retry: false })}>
      <MemoryRouter initialEntries={[entry]}>
        <App />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}
