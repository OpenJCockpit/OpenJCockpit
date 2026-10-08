import React from 'react';
import { createRoot } from 'react-dom/client';
import './styles.scss';
import App from './App';

// StrictMode is not enabled here. The original Arwes-related justification no longer
// applies (Arwes has been removed from this app), but this app's existing effects have
// never been verified under StrictMode double-invocation, and this pattern-only change
// does not cover that verification. Enabling StrictMode is a deliberate, separate
// follow-up, not implied by any remaining technical constraint.
createRoot(document.getElementById('root') as HTMLElement).render(<App />);
