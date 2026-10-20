import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import App from '../App';

/** Renders the full App at `entry`; the router is memory-backed so URLs never leak between tests. */
export function renderRoutedApp(entry = '/') {
  return render(
    <MemoryRouter initialEntries={[entry]}>
      <App />
    </MemoryRouter>,
  );
}
