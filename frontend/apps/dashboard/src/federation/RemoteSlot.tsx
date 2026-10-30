import { Component, Suspense, use, type ReactNode } from 'react';
import type { LazyComponent } from '@openjcockpit/hub-contract';

/** Anything a remote contributes as a lazily loaded component: a top bar item or a widget. */
type RemoteItem = { component: LazyComponent };

/** A failed remote component leaves its slot empty instead of breaking the surrounding page. */
class EmptyOnError extends Component<{ children: ReactNode }, { failed: boolean }> {
  override state = { failed: false };

  static getDerivedStateFromError() {
    return { failed: true };
  }

  override componentDidCatch(error: Error) {
    console.error('A remote component failed to render', error);
  }

  override render() {
    return this.state.failed ? null : this.props.children;
  }
}

// One load per definition for the page's lifetime; the promise is cached so re-renders do not refetch.
const loads = new WeakMap<RemoteItem, ReturnType<RemoteItem['component']>>();

function loadOnce(item: RemoteItem) {
  let load = loads.get(item);
  if (!load) {
    load = item.component();
    loads.set(item, load);
  }
  return load;
}

function Loaded({ item }: { item: RemoteItem }) {
  const { default: Item } = use(loadOnce(item));
  return <Item />;
}

export function RemoteSlot({ item }: { item: RemoteItem }) {
  return (
    <EmptyOnError>
      <Suspense fallback={null}>
        <Loaded item={item} />
      </Suspense>
    </EmptyOnError>
  );
}
