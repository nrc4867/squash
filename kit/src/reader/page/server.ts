import { createServer, type IncomingMessage, type Server, type ServerResponse } from "node:http";
import type { HeldMark } from "../readings.ts";
import type { Evidence, HidingLayer } from "../types.ts";

/** The edit page's default port: clear of the recipe server's 47830 and its neighbours. */
export const EDIT_PORT = 47842;

/** The largest request body taken, in bytes. */
const MOST_BYTES = 1024 * 1024;
const HOST = "127.0.0.1";

/** What the edit server serves: the page, a proposal again from held marks and layers, and Save, which keeps them. */
export interface EditServer {
  port: number;
  page: () => string;
  propose: (held: HeldMark[], layers: HidingLayer[]) => Evidence;
  save: (held: HeldMark[], layers: HidingLayer[]) => string;
}

function body(request: IncomingMessage): Promise<string> {
  return new Promise((resolve, reject) => {
    const chunks: Buffer[] = [];
    let size = 0;
    request.on("data", (chunk: Buffer) => {
      size += chunk.length;
      if (size > MOST_BYTES) {
        reject(new Error("request too large"));
        request.destroy();
      } else chunks.push(chunk);
    });
    request.on("end", () => resolve(Buffer.concat(chunks).toString("utf8")));
    request.on("error", reject);
  });
}

function send(response: ServerResponse, code: number, type: string, text: string): void {
  response.writeHead(code, { "content-type": type, "cache-control": "no-store" });
  response.end(text);
}

/** Held marks and layers from a request, each checked for its shape. */
function edits(text: string): { held: HeldMark[]; layers: HidingLayer[] } {
  const value = JSON.parse(text) as { held?: unknown; layers?: unknown };
  const held = Array.isArray(value.held) ? (value.held as HeldMark[]).filter((h) => typeof h.id === "string" && Array.isArray(h.at)) : [];
  const layers = Array.isArray(value.layers) ? (value.layers as HidingLayer[]).filter((l) => Array.isArray(l.box) && typeof l.looseness === "number") : [];
  return { held, layers };
}

async function answer(server: EditServer, request: IncomingMessage, response: ServerResponse): Promise<void> {
  if (request.method === "GET" && (request.url === "/" || request.url === "/index.html")) return send(response, 200, "text/html; charset=utf-8", server.page());
  if (request.method === "POST" && request.url === "/propose") {
    const { held, layers } = edits(await body(request));
    return send(response, 200, "application/json", JSON.stringify({ evidence: server.propose(held, layers) }));
  }
  if (request.method === "POST" && request.url === "/save") {
    const { held, layers } = edits(await body(request));
    return send(response, 200, "application/json", JSON.stringify({ ok: true, path: server.save(held, layers) }));
  }
  send(response, 404, "text/plain", "not found");
}

/** Serves the edit page on 127.0.0.1 until the process stops. */
export function serveEditPage(server: EditServer): Server {
  const http = createServer((request, response) => {
    answer(server, request, response).catch((error: unknown) => send(response, 500, "application/json", JSON.stringify({ error: String(error) })));
  });
  http.listen(server.port, HOST, () => console.log(`edit at http://${HOST}:${server.port}/ ; Ctrl-C stops`));
  return http;
}
