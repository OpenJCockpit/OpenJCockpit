import { Component, Suspense, use, type ReactNode } from 'react';
import type { TopbarDefinition } from '@openjcockpit/hub-contract';

/** A failed remote component leaves its slot empty instead of breaking the surrounding chrome. */
class EmptyOnError extends Component<{ children: ReactNode }, { failed: boolean }> {
  override state = { failed: false };

  static getDerivedStateFromError() {
    return { failed: true };
  }

  override componentDidCatch(error: Error) {
    console.error('A top bar item failed to render', error);
  }

  override render() {
    return this.state.failed ? null : this.props.children;
  }
}

// One load per definition for the page's lifetime; the promise is cached so re-renders do not refetch.
const loads = new WeakMap<TopbarDefinition, ReturnType<TopbarDefinition['component']>>();

function loadOnce(item: TopbarDefinition) {
  let load = loads.get(item);
  if (!load) {
    load = item.component();
    loads.set(item, load);
  }
  return load;
}

function Loaded({ item }: { item: TopbarDefinition }) {
  const { default: Item } = use(loadOnce(item));
  return <Item />;
}

export function TopbarSlot({ item }: { item: TopbarDefinition }) {
  return (
    <EmptyOnError>
      <Suspense fallback={null}>
        <Loaded item={item} />
      </Suspense>
    </EmptyOnError>
  );
}
