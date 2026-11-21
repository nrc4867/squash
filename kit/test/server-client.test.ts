// The recipe server's client: what it sends, how it prints a streamed build, and when tools/recipe.sh builds in its own JVM instead.
import { test } from "node:test";
import assert from "node:assert/strict";
import { DEFAULT_SERVER_PORT, FALL_BACK, lines, sendBuild, serverPort, type Printer, type ServerBuild } from "../src/run/server.ts";

const build: ServerBuild = { port: 47830, engine: "abc123", args: ["root=C:/squash", "recipe=C:/models/fox.recipe.json"], watch: false };

function printer(): Printer & { printed: string[] } {
  const printed: string[] = [];
  return { printed, out: (line) => printed.push(`out ${line}`), err: (line) => printed.push(`err ${line}`) };
}

/** A body sent in the given chunks, which may split lines anywhere. */
function streamed(...chunks: string[]): ReadableStream<Uint8Array> {
  const encoder = new TextEncoder();
  return new ReadableStream({
    start(controller) {
      chunks.forEach((chunk) => controller.enqueue(encoder.encode(chunk)));
      controller.close();
    },
  });
}

function answering(status: number, body: ReadableStream<Uint8Array> | null, seen: { url?: string; body?: unknown } = {}): typeof fetch {
  return (async (url: string | URL | Request, init?: RequestInit) => {
    seen.url = String(url);
    seen.body = JSON.parse(String(init?.body));
    return new Response(body, { status });
  }) as typeof fetch;
}

test("no server listening falls back", async () => {
  const refused = (async () => {
    throw new TypeError("fetch failed");
  }) as typeof fetch;
  assert.equal(await sendBuild(build, refused, printer()), FALL_BACK);
});

test("a server on another engine or stopping falls back", async () => {
  for (const status of [409, 503, 400]) {
    assert.equal(await sendBuild(build, answering(status, streamed('{"error":"x"}')), printer()), FALL_BACK);
  }
});

test("a build is sent with its engine and streamed lines are printed in order, its code returned", async () => {
  const seen: { url?: string; body?: unknown } = {};
  const print = printer();
  const body = streamed('{"out":"recipe: built 12 com', 'mands in 40 ms"}\n{"err":"recipe warning at $.parts"}\n', '{"done":{"code":2,"files":[]}}\n');
  assert.equal(await sendBuild(build, answering(200, body, seen), print), 2);
  assert.equal(seen.url, "http://127.0.0.1:47830/build");
  assert.deepEqual(seen.body, { args: build.args, engine: "abc123", stream: true });
  assert.deepEqual(print.printed, ["out recipe: built 12 commands in 40 ms", "err recipe warning at $.parts"]);
});

test("an answer that ends before the build falls back", async () => {
  const print = printer();
  assert.equal(await sendBuild(build, answering(200, streamed('{"out":"recipe: built"}\n')), print), FALL_BACK);
  assert.match(print.printed.at(-1) ?? "", /^err recipe server: /);
});

test("a watch goes to /watch and falls back when the server goes for a new engine", async () => {
  const seen: { url?: string } = {};
  const body = streamed('{"built":{"code":0}}\n{"done":{"code":0,"reconnect":true}}\n');
  assert.equal(await sendBuild({ ...build, watch: true }, answering(200, body, seen), printer()), FALL_BACK);
  assert.equal(seen.url, "http://127.0.0.1:47830/watch");
});

test("the port comes from SQUASH_RECIPE_PORT when it is a number", () => {
  assert.equal(serverPort({ SQUASH_RECIPE_PORT: "48000" }), 48000);
  assert.equal(serverPort({ SQUASH_RECIPE_PORT: "x" }), DEFAULT_SERVER_PORT);
  assert.equal(serverPort({}), DEFAULT_SERVER_PORT);
});

test("lines keep a last line with no newline", async () => {
  const read: string[] = [];
  for await (const line of lines(streamed("a\nb", "c\n", "d"))) read.push(line);
  assert.deepEqual(read, ["a", "bc", "d"]);
});
