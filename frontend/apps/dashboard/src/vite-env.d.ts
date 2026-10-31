/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Comma-separated origins remote code may load from, besides the host's own. */
  readonly VITE_FEDERATION_ALLOWED_ORIGINS?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
