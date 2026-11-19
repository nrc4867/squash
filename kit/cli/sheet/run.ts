import { sheetCommands } from "./commands.ts";
import { SheetOptions } from "./options.ts";

export const SHEET_USAGE = `usage: tools/recipe.sh kit sheet <command> <image> [option ...]   reads a character sheet (PNG or JPEG)
  views     split the sheet into views: views.png (every box numbered), <view>.png each, views.json
  measure   one view's silhouette and landmarks in heads and body units: measure-<view>.json
  grid      one view with half-head lines and landmark lines: grid-<view>.png and measure-<view>.json
  zoom      region=head|hands|feet or box=x,y,w,h scaled 2-4 times (scale=): zoom-<name>.png
  palette   one view's colours, ink left out (colors=8): palette-<view>.png and palette-<view>.json
  profiles  torso, leg and arm radii for tube(): front=<view> side=<view|image> stations=7 facing=left|right: profiles.json
  trace     part=head|ear-left|ear-right|tail|arm-left|arm-right|figure or box=x,y,w,h, tolerance=, open=true: trace-<part>.json/.png
Options: out=<folder> (default <image>-sheet beside it), view=<index|name|facing>, box=x,y,w,h (one view), boxes=x,y,w,h;...,
names=front,threeq,back, ground=#rrggbb, groundTolerance=<0-255>, holes=x,y;... (enclosed background), height=<body units, ground to crown, default 1>, heightTo=top (height to the highest point), landmarks=name:x,y;...`;

/** Runs one sheet command on an image; returns the exit code. */
export function runSheet(command: string | undefined, image: string | undefined, rest: readonly string[]): number {
  const run = command ? sheetCommands[command] : undefined;
  if (!run || !image) {
    console.error(SHEET_USAGE);
    return 2;
  }
  run(image, new SheetOptions(rest));
  return 0;
}
