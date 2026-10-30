import { createContext } from 'react';
import { loadRemote } from '@module-federation/enhanced/runtime';
import {
  HUB_CONTRACT_VERSION,
  isAppDefinition,
  type AppDefinition,
} from '@openjcockpit/hub-contract';

export const REMOTE_LOAD_TIMEOUT_MS = 5000;

export interface RemoteApps {
  apps: AppDefinition[];
  /** Names of remotes that were down, hung, or exported something that is not an app. */
  failed: string[];
}

export interface RemoteModule {
  app?: unknown;
  contractVersion?: unknown;
}

export type RemoteLoader = (name: string) => Promise<RemoteModule | null | undefined>;

export const RemoteFailuresContext = createContext<readonly string[]>([]);

const loadExposedApp: RemoteLoader = (name) => loadRemote<RemoteModule>(`${name}/app`);

function withTimeout<T>(promise: Promise<T>, ms: number, name: string): Promise<T> {
  let timer: ReturnType<typeof setTimeout> | undefined;
  const timeout = new Promise<never>((_, reject) => {
    timer = setTimeout(() => reject(new Error(`Remote "${name}" timed out after ${ms} ms`)), ms);
  });
  return Promise.race([promise, timeout]).finally(() => clearTimeout(timer));
}

async function loadOne(
  name: string,
  load: RemoteLoader,
  timeoutMs: number,
): Promise<AppDefinition> {
  const module = await withTimeout(load(name), timeoutMs, name);
  if (!module || !isAppDefinition(module.app)) {
    throw new Error(`Remote "${name}" does not export a valid app`);
  }
  const version = module.contractVersion ?? 1;
  if (typeof version !== 'number' || version > HUB_CONTRACT_VERSION) {
    throw new Error(
      `Remote "${name}" needs contract version ${String(version)}, host has ${HUB_CONTRACT_VERSION}`,
    );
  }
  return module.app;
}

/** Loads every remote independently. A failing remote is reported and skipped; this never throws. */
export async function loadRemoteApps(
  names: readonly string[],
  load: RemoteLoader = loadExposedApp,
  timeoutMs: number = REMOTE_LOAD_TIMEOUT_MS,
): Promise<RemoteApps> {
  const settled = await Promise.allSettled(names.map((name) => loadOne(name, load, timeoutMs)));
  const result: RemoteApps = { apps: [], failed: [] };
  settled.forEach((outcome, index) => {
    if (outcome.status === 'fulfilled') {
      result.apps.push(outcome.value);
    } else {
      result.failed.push(names[index] as string);
      console.error(`Remote "${names[index]}" failed to load`, outcome.reason);
    }
  });
  return result;
}
