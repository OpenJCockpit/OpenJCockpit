import { Link } from 'react-router';

/* eslint-disable i18next/no-literal-string -- proof-of-federation top bar item; remote translations arrive with the i18n step */
/** Top bar item with no data source, to prove a remote can fill a header slot. */
export default function HelloTopbar() {
  return (
    <Link to="/hello" data-testid="hello-topbar">
      Hello
    </Link>
  );
}
