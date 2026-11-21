// The recipe server's client: tools/recipe.sh sends a build here first, and builds in its own JVM when this exits FALL_BACK.

/** The port the recipe server listens on unless SQUASH_RECIPE_PORT names another; the server's own default. */
export const DEFAULT_SERVER_PORT = 47830;

/** The exit code when no server with this engine took the build, so tools/recipe.sh builds in a JVM of its own. */
export const FALL_BACK = 75;

export interface ServerBuild {
  port: number;
  /** The engine version the checkout runs; a server with another one refuses the build. */
  engine: string;
  /** The recipe command's arguments as tools/recipe.sh writes them: `key=value`, spaces percent-encoded, `root=` among them. */
  args: readonly string[];
  /** Build again on each save of the recipe, until the client stops or the server goes. */
  watch: boolean;
}

/** Where the build's lines go. */
export interface Printer {
  out(line: string): void;
  err(line: string): void;
}

/** One line of the server's streamed answer. */
interface ServerEvent {
  out?: string;
  err?: string;
  done?: { code: number; reconnect?: boolean };
}

export const consolePrinter: Printer = {
  out: (line) => process.stdout.write(line + "\n"),
  err: (line) => process.stderr.write(line + "\n"),
};

/** The server's port: SQUASH_RECIPE_PORT when it is a number, else the default. */
export function serverPort(env: NodeJS.ProcessEnv = process.env): number {
  const named = Number(env.SQUASH_RECIPE_PORT);
  return Number.isInteger(named) && named > 0 ? named : DEFAULT_SERVER_PORT;
}

/**
 * Sends `build` to the server on 127.0.0.1 and prints its log as it comes: the build's exit code, or FALL_BACK when no server answers,
 * it refuses the build (another engine, or stopping for a new one), or the answer ends before the build does.
 */
export async function sendBuild(build: ServerBuild, fetcher: typeof fetch = fetch, print: Printer = consolePrinter): Promise<number> {
  let response: Response;
  try {
    response = await fetcher(`http://127.0.0.1:${build.port}/${build.watch ? "watch" : "build"}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ args: build.args, engine: build.engine, stream: true }),
    });
  } catch {
    return FALL_BACK;
  }
  if (response.status !== 200 || !response.body) {
    await response.body?.cancel();
    return FALL_BACK;
  }
  let code: number | undefined;
  for await (const line of lines(response.body)) {
    const event = parseEvent(line);
    if (!event) continue;
    if (event.out !== undefined) print.out(event.out);
    if (event.err !== undefined) print.err(event.err);
    if (event.done) code = event.done.reconnect ? FALL_BACK : event.done.code;
  }
  if (code === undefined) {
    print.err("recipe server: the answer ended before the build; building here instead");
    return FALL_BACK;
  }
  return code;
}

function parseEvent(line: string): ServerEvent | undefined {
  if (line.trim() === "") return undefined;
  try {
    return JSON.parse(line) as ServerEvent;
  } catch {
    return undefined;
  }
}

/** The text of `body` cut into lines, a line split across chunks joined. */
export async function* lines(body: AsyncIterable<Uint8Array>): AsyncGenerator<string> {
  const decoder = new TextDecoder();
  let pending = "";
  for await (const chunk of body) {
    pending += decoder.decode(chunk, { stream: true });
    let end: number;
    while ((end = pending.indexOf("\n")) >= 0) {
      yield pending.slice(0, end);
      pending = pending.slice(end + 1);
    }
  }
  pending += decoder.decode();
  if (pending.length > 0) yield pending;
}
