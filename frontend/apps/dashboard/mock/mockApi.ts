import type { IncomingMessage } from 'node:http';
import type { Plugin } from 'vite';
import { faultControl, faultFor } from './faults';
import { routes, type Ctx } from './routes';
import { MockError } from './store';

// Dev-only: answers /api/* from an in-memory store so the UI runs without the backend. Enabled by
// MOCK_API=1 in vite.config.ts (`npm run start:hub:mock` starts the whole hub that way) and never
// registered for builds. Endpoints live in routes.ts, state and seed data in store.ts.
async function readBody(req: IncomingMessage): Promise<Ctx['body']> {
  const chunks: Buffer[] = [];
  for await (const chunk of req) chunks.push(chunk as Buffer);
  try {
    const parsed: unknown = JSON.parse(Buffer.concat(chunks).toString() || '{}');
    return typeof parsed === 'object' && parsed !== null ? (parsed as Ctx['body']) : {};
  } catch {
    return {};
  }
}

export function mockApi(): Plugin {
  return {
    name: 'openjcockpit-mock-api',
    configureServer(server) {
      server.middlewares.use(async (req, res, next) => {
        const { pathname, searchParams } = new URL(req.url ?? '/', 'http://localhost');
        const isControl = pathname.startsWith('/__mock/');
        if (!isControl && !pathname.startsWith('/api/')) return next();
        res.setHeader('Content-Type', 'application/json');
        const send = (status: number, body: unknown) => {
          res.statusCode = status;
          res.end(JSON.stringify(body));
        };
        if (isControl) {
          const result = faultControl(pathname, searchParams);
          return result === undefined ? next() : send(200, result);
        }
        const fault = faultFor(req.method, pathname);
        if (fault?.delayMs) await new Promise((resolve) => setTimeout(resolve, fault.delayMs));
        if (fault?.status) return send(fault.status, { message: fault.message, code: fault.code });
        for (const [method, pattern, handler] of routes) {
          const match = method === req.method ? pattern.exec(pathname) : null;
          if (!match) continue;
          try {
            const body = req.method === 'GET' ? {} : await readBody(req);
            const p = match.slice(1).map(decodeURIComponent);
            return send(200, handler({ p, query: searchParams, body }) ?? {});
          } catch (error) {
            if (error instanceof MockError) {
              return send(error.status, { message: error.message, code: error.code });
            }
            server.config.logger.error(`[mock-api] ${req.method} ${pathname}: ${String(error)}`);
            return send(500, { message: 'Mock API error', code: 'MOCK_ERROR' });
          }
        }
        server.config.logger.warn(`[mock-api] no mock for ${req.method} ${pathname}`);
        send(404, { message: `No mock for ${req.method} ${pathname}`, code: 'MOCK_NOT_FOUND' });
      });
    },
  };
}
