/**
 * Minimal ambient typing for the Node globals this package touches
 * (`process.env`). Deliberately not `@types/node`: the package's dependency
 * list is exactly three, pinned, justified devDependencies (architecture
 * §5.1) and a full Node type-definitions package is not needed for the one
 * global this harness reads.
 */
declare const process: {
  env: Record<string, string | undefined>;
};
