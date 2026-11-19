# Reading a reference

A reference is usually one sheet: a few views of the character (front, three-quarter, side, back), any size, anywhere on the page, often at different scales, with labels, a title, colour swatches, ground lines and shadows round them. Sometimes it is separate images, or a description in words. Read it with the kit's sheet tools before writing a part; every number you take from it goes in the file as a named constant with the tool that gave it.

All of them: `tools/recipe.sh kit sheet <command> <image> [options]`, writing JSON and images to `<image>-sheet/` beside the image (or `out=`). Their options, outputs and every landmark's definition are in `kit/README.md`, Reading a character sheet. **Look at every image they write**: they are drawn to be read by eye, and a wrong landmark is only caught by looking. The tools are a first draft of the measurements, not ground truth: shoulders, armpits, crotches and knees in particular can sit on the wrong feature.

## 1. Find the views

```
tools/recipe.sh kit sheet views C:/refs/pip.png names=front,side,back
```

It prints each view's box, pixel height and facing guess with its confidence, and the `other` boxes (labels, swatches, the title). Open `views.png`: every view boxed and numbered, shadows dashed, clutter grey. Then:

- Name the views in reading order with `names=` and pass the same `names=` to every later command, so `view=side` means the same view each time.
- Two figures drawn overlapping read as one view: give `boxes=x,y,w,h;x,y,w,h`. A detached piece (a floating tail tip) joins the nearest figure; check it joined the right one.
- A background the figure closes off (a tail curled against a leg, the gap under an arm on the hip) stays figure: open it with `holes=x,y`.
- An off-white, graded or textured page is keyed from the border; a figure that bleeds into the page needs `ground=#rrggbb` and `groundTolerance=`.
- White socks, pale hands and cream sleeves within the paper's tolerance are kept as figure; a view still cut off at its feet or a hand needs `groundTolerance=` lower, or `boxes=`.
- The facing guess carries a `reason`. A back is told by a head that is one colour, so it is dependable; front against three-quarter is not (a turned body with its face to the viewer reads front, a raised arm pulls a front toward three-quarter). Trust your eyes over it and name views yourself.

## 2. Heights: grid and measure

```
tools/recipe.sh kit sheet grid C:/refs/pip.png names=front,side,back view=front height=1
```

`height=` is the character's height in body units from the ground to the crown (ears, buns and tufts above the skull do not count; `heightTo=top` counts them). Pick it once and keep it: the whole body is drawn in those units. A person-sized biped is about 1.6 to 1.7, which suits the templates at `size` 1 (`proportions.md`); a small creature can be 1, and its templated hands and feet then take a `size` below 1.

`grid-<view>.png` draws a line every half head from the crown, labelled `1.5H` and its sheet row, and a line a landmark with its height: **green found, magenta unsure, orange estimated, blue given.** `measure` prints the same as a table, with the reason under every landmark that is not found, and the proportion checks it failed:

```
  view 1 front (front?): 3.04 heads, head 0.3286 of 1, axis x 243, ground y 641
  crown        1.0000   3.04H  found     y 218
  chin         0.6714   2.04H  found     y 357
  ...
  crotch       0.2388   0.73H  found     y 540
  knee         0.1537   0.47H  estimated y 576
```

### Check every landmark before using the numbers

This step is required before any number from `measure` or `profiles` goes into the file.

```
tools/recipe.sh kit sheet check C:/refs/pip.png names=front,side,back view=front
```

`check-<view>.png` is one close-up a landmark, side by side, each with its line across it, the outline points it was read from crossed, and its status and reason beneath. Look at each tile and ask whether the line sits on the feature it names: the shoulder where the outline turns from the neck into the arm (not the collar), the armpit where the arm's outline leaves the torso under the sleeve (not the tee's hem), the crotch at the top of the gap between the legs (not the shorts' hem), the knee and ankle on the leg. Fix every wrong one by reading its pixel off the image and passing `landmarks=armpit:190,400;shoulder:170,330` to every later command, then run `check` again until every tile is right. Do not trust the colour alone: a green line can be on the wrong feature, and magenta (unsure) and orange (estimated) say the tool doubts itself, with the reason under the tile (`no gap between an arm and the torso`, `two methods disagree by 0.15 heads`). Arms held against the body, raised arms, a loose shirt or a big head are where it is wrong most.

Record each landmark in the feature list as found (the tool's line was right), fixed (you gave it with `landmarks=`) or estimated (nothing in the image shows it; say how you placed it).

Read every line against the grid image:

- **Found is not always right.** Arms held against the body make the hand tips or the sleeve's edge read as the armpit; shorts' hems read as the crotch; a big hairdo as wide as the head reads as skull. Given landmarks (blue) replace what was found and steer what follows; re-run until every line sits on its feature.
- **Unsure** (magenta) means read from the outline but doubtful, or failing a proportion check against the other landmarks: confirm it on the check image.
- **Estimated** (orange) means placed by proportion: legs drawn touching hide the crotch, straight legs hide the knee. Take the knee from the side view, where it shows as the leg's front edge turning, or from the description; else keep the estimate and say so in the feature list.
- **The head count** (`3.04 heads`) is the most important number on the sheet: a cartoon creature is 2 to 4 heads tall, a stylised person 5 to 7.5. Write it into the feature list and check the build against it (`judging.md`).
- Measure the side view too (`view=side`): it gives the depths the front cannot, and where the knees and the seat really are.

A landmark on a clothing edge (a sweater's ribbed hem for the hip, a cuff or a sock's top for the ankle) is magenta with that reason, and a waist the clothes hide is orange: the body's own is under the cloth, so place it by eye from the garment's hem and the description. Every landmark below the neck of a three-quarter view is magenta with a `warnings` line: read proportions from the front, side and back views and take the three-quarter for the face, hair and pose only. A crown raised by tufts or big hair carries a warning too: set `landmarks=crown:x,y` where the skull tops out when the head count looks wrong.

### Measure the hands, feet and face

`measure` and `zoom` also read the sizes that make a toon figure look right or wrong: `handLength`, `handWidth`, `footLength`, `footWidth`, and the face's `eyeLine`, `eyeWidth`, `eyeSpacing`, `nose`, `mouth`, `chinLength`, each as a share of the head's height and of the figure's height, with the same found, unsure, estimated and reason (`kit/README.md`, Reading a character sheet). Hands, feet and faces built too small or too plain are the commonest miss: read them here before drawing them.

```
tools/recipe.sh kit sheet measure C:/refs/pip.png names=front,side,back view=front height=1.6
tools/recipe.sh kit sheet zoom C:/refs/pip.png names=front,side,back view=side region=feet height=1.6
```

A hand is read in the view that shows it apart from the body (a raised or A-pose arm), a foot's width in the front or back view and its length in the side view, the face in the front view. What a view cannot give is listed with why (`not read: a front view foreshortens the foot`); read it from another view, or give the marks by eye (`landmarks=wrist:x,y;handTip:x,y`, `heel:x,y;toeTip:x,y`) after looking at the `zoom` image, whose drawn lines show where each reading was taken. Check each on the zoom before trusting it: a spread hand reads as wide as its fingers, a pale eye white in pale skin reads as its iris and lid. `measure` writes `targets-<view>.json`: declare the proportions you trust in the file as `targets: targets({ handLength: "0.38 head", footLength: "0.45 head" })` (or pass `targets=<that file>` to the build), and after every build read the `proportions` and `FLAG` lines in `measure.txt` (`judging.md`). Fix every flag before judging by eye.

`plan` in `measure-<view>.json` holds every landmark's height ready for `bodyPlan`; `widths` the half widths at head, neck, chest, waist, hip, thigh, knee and ankle; `pieces` the ears, arms, tail and anything else outside the core with their tips.

## 3. Colours: palette

```
tools/recipe.sh kit sheet palette C:/refs/pip.png names=front,side,back view=front colors=8
```

One tile a colour with its hex, its share of the figure and where its pixels are, the ink left out. Name each colour for what it is on the character (`fur`, `cream`, `inner-ear`, `tee`), never for its hue, and read the other views' palettes for colours the front hides (a back patch, a tail tip). A sheet's own swatches, when it has them, are the artist's intended colours: prefer them to sampled pixels, which carry shading. Thick ink beside a dark area can show as a colour of its own; drop it. `colour-and-marks.md` turns them into slots.

## 4. Tube radii: profiles

```
tools/recipe.sh kit sheet profiles C:/refs/pip.png names=front,side,back front=front side=side stations=7 landmarks=crotch:243,556
```

`profiles.json` gives the torso (resting up: `left`/`right` from the front, `front`/`back` from the side), each leg (resting down: `outer`/`inner`, `front`/`back`) and each loose arm, as radii at stations ready for `tube`, with `confidence` and `notes`. Pass the same `landmarks=` you fixed on the check image: the torso runs crotch to neck, the legs crotch to ankle. Treat them as a first draft (a `warnings` line says when the front or side view is guessed three-quarter; every radius is then `low` confidence):

- Above the armpits the torso is held to the armpit's width where the arms join it: draw the chest and shoulders yourself from the grid.
- Clothes are silhouette: a baggy tee or shorts widen the torso and legs. Take the body under them thinner (about the fit of the garment, `clothing.md`) and let the garment stand off it.
- A leg's front and back come from the side view, where both legs overlap: the nearer leg's outline.

In code, the same numbers without the files (`squash-kit/sheet`):

```ts
import { measureView, openSheet, sheetProfiles } from "squash-kit/sheet";
import { bodyPlan, tube } from "squash-kit";

const sheet = openSheet("C:/refs/pip.png", { names: ["front", "side", "back"] });
const front = measureView(sheet, "front", { height: 1, landmarks: "crotch:243,556" });
const side = measureView(sheet, "side", { height: 1 });
const plan = bodyPlan({ crotch: front.measurement.plan.crotch, neck: front.measurement.plan.neck }, { hips: 0.15 });
const torso = sheetProfiles(front, side).torso;
const torsoOutlines = torso ? tube({ ...torso, closed: true }) : undefined;
```

Reading the sheet in the character file ties the build to the image's path; copying the numbers in as constants keeps the file standing alone. Copy them.

## 5. Parts up close: zoom and trace

```
tools/recipe.sh kit sheet zoom C:/refs/pip.png names=front,side,back view=side region=head
tools/recipe.sh kit sheet trace C:/refs/pip.png names=front,side,back view=front part=ear-right open=true
```

`zoom` scales a region 2 to 4 times with crisp edges: `region=head`, `hands`, `feet`, or `box=x,y,w,h` for anything else (a tail tip, a buckle, a print). Zoom the head in every view before the face (`head-and-face.md`), the hands and feet before choosing their templates, and every garment edge before painting it.

`trace` gives a part's outline as a few points in body units, x from the figure's axis, y up from the ground: `part=head`, `ear-left`, `ear-right`, `tail`, `arm-left`, `arm-right` (left and right as the image shows them, not the character's), `figure`, or `box=x,y,w,h`; `open=true` for an open outline, `tolerance=` for fewer or more points. The points are in sheet space, not in the part's own outline x and y: use them to read lengths, widths and the shape of a tip (an ear is 0.2 long and 0.15 wide with a round tip), then draw the part in its own frame from those numbers with the kit's shapes (`leaf`, `oval`, `teardrop`, `tube`).

## 6. The feature list

Before the first part, write the list into the file as `"x-features"`, one line a feature, in plain words, each with the view that shows it:

- proportions: head count, head to body, leg length, how tall against how wide or long;
- every part's silhouette: head shape, muzzle or snout, ears (shape, cup, how they sit), tail (length, thickness, curl), hands (digits), feet or paws (toes, claws, pads);
- every colour region on the face and body: a two-tone face, a muzzle patch, a belly, inner ears, a tail tip, socks;
- the eyes (shape, size, spacing, height on the face, highlights), the nose, the mouth, brows, cheeks, whiskers and marks;
- every garment and accessory: its hems, openings, colours, prints and what comes through it.

## Limits

- The sheet tools read silhouettes and colours, not lines inside the figure: a sleeve drawn over the arm, a tee's hem, the eye's lid shape are read by eye from `zoom`.
- A description in words has no measurements: write the plan's heights from the head count it gives (or a stated height), and say in the feature list which numbers were chosen rather than read.
