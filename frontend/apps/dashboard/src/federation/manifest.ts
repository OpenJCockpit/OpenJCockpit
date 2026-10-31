import { registerRemotes } from '@module-federation/enhanced/runtime';

export type FederationManifest = Record<string, string>;

const DEV_ORIGIN = /^https?:\/\/localhost(:\d+)?$/;

function originOf(url: string): string | null {
  try {
    return new URL(url, window.location.href).origin;
  } catch {
    return null;
  }
}

export function isAllowedOrigin(
  url: string,
  allowedOrigins: readonly string[],
  dev: boolean,
): boolean {
  const origin = originOf(url);
  if (!origin) return false;
  return (
    origin === window.location.origin ||
    allowedOrigins.includes(origin) ||
    (dev && DEV_ORIGIN.test(origin))
  );
}

function parseManifest(body: unknown): FederationManifest {
  if (typeof body !== 'object' || body === null || Array.isArray(body)) {
    throw new Error('federation.manifest.json must be an object of name to URL');
  }
  const manifest: FederationManifest = {};
  for (const [name, entry] of Object.entries(body)) {
    if (typeof entry !== 'string') throw new Error(`Manifest entry "${name}" must be a URL string`);
    manifest[name] = entry;
  }
  return manifest;
}

export async function fetchManifest(): Promise<FederationManifest> {
  const response = await fetch('/federation.manifest.json', { cache: 'no-store' });
  if (!response.ok) throw new Error(`federation.manifest.json responded ${response.status}`);
  return parseManifest(await response.json());
}

/** Origins the host may load remote code from, besides its own: runtime /config.json, then build time. */
export async function fetchAllowedOrigins(): Promise<string[]> {
  try {
    const response = await fetch('/config.json', { cache: 'no-store' });
    if (response.ok) {
      const body = (await response.json()) as { federation?: { allowedOrigins?: unknown } };
      const origins = body.federation?.allowedOrigins;
      if (Array.isArray(origins)) return origins.filter((o): o is string => typeof o === 'string');
    }
  } catch {
    // Not served or not JSON: fall back to the build-time list.
  }
  return (import.meta.env.VITE_FEDERATION_ALLOWED_ORIGINS ?? '').split(',').filter(Boolean);
}

/**
 * Registers the manifest's remotes at runtime (not as static plugin remotes, which would be baked
 * into the host build). Entries from a disallowed origin are dropped and returned as rejected.
 */
export function registerManifestRemotes(
  manifest: FederationManifest,
  allowedOrigins: readonly string[],
  dev = import.meta.env.DEV,
): { names: string[]; rejected: string[] } {
  const names: string[] = [];
  const rejected: string[] = [];
  const entries: { name: string; entry: string }[] = [];
  for (const [name, entry] of Object.entries(manifest)) {
    if (isAllowedOrigin(entry, allowedOrigins, dev)) {
      names.push(name);
      entries.push({ name, entry });
    } else {
      rejected.push(name);
      console.error(`Remote "${name}" rejected: origin of ${entry} is not allowed`);
    }
  }
  registerRemotes(entries);
  return { names, rejected };
}
