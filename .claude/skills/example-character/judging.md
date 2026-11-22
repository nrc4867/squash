# Judging

Judge after every build, against the sheet, not against what you meant. A run prints every file it wrote: open every PNG, then `measure.txt`. A feature not seen in a render is not done, whatever the recipe says.

## 1. Matched views beside the sheet

- `<outfit>.png` holds front, three-quarter and side; `<outfit>-back-top.png` the back and the view from above. Put each beside the sheet's view of the same side (the crops `kit sheet views` wrote) and compare part by part: the head's height against the body's, where the shoulders, hips, knees and feet fall, each part's silhouette, each colour's edge.
- **Count heads** on the front render: crown to chin, then down to the soles. It must match the sheet's head count (`reading-a-reference.md`) within a tenth of a head.
- From the side: the head over the shoulders, the belly and seat, the knees, the heels under the ankles, the tail's curve. From behind: the back of the head, the ears' backs, the tail's root, the seat, garment backs. From above: the ears' and feet's splay, the tail's swing, the shoulders' width.

## 2. A reference camera fit

```
tools/recipe.sh kit <file.ts> reference=<sheet>-sheet/front.png fit=true
```

With a single view cropped from the sheet (`kit sheet views` writes `<view>.png`, other figures painted out), `fit=true` searches the camera that best matches its silhouette (round, up, distance, target, field of view), prints it as `fit: eye=... target=... fov=...` (pass it as given on later runs), and writes `compare/pair.png` (reference left), `compare/overlay.png` (blue reference only, red Squash only, grey both) and the silhouette IoU. Through a fitted camera a low IoU is shape, not camera: read the overlay's blue and red for the part that is too small or too big. Do it for the front and the side view.

**A fit can land on the wrong side.** A silhouette is nearly the same from behind and from a turned front, so the search may settle on a back three-quarter for a front reference; it prints its best camera and the best from the far side. Open `compare/pair.png` and check the face shows; if not, pass the other camera it printed. For a straight front or side comparison skip the search and give the camera: in the compare frame the character faces +z, so the front is `eye=0,<mid height>,2.5 target=0,<mid height>,0 fov=30` and its left side `eye=2.5,<mid height>,0`. The pair beside the sheet's view is the most useful image of the run. Judge shape and dressing, not tone: light and shading are Squash's own. The compare frame is metres, +y up, the character facing +z with its left at +x; `scale=` gives body units a metre for a character drawn at another size.

## 3. Close-ups

- `close=face,hand,foot` (the default), `close=hands,feet` (bare, at rest, curled, open, spread, from six sides), `close=joins` (neck, shoulder, hip), and **any part id**: `close=ear-l,tail,head`, framed close per outfit as `<outfit>-<id>.png`.
- **`close=strip`, the pose strip, accompanies every review of a body change**: `<outfit>-strip.png` shows the display pose, rest and a T-pose side by side, which the display pose alone hides shoulder and underarm faults. Look at the shoulder and underarm in the T panel and the rest panel before the display one.
- `only=head,ear-l` draws just those parts in every view: the fastest look at one part.
- Each expression renders its face as `<outfit>-face-<expression>.png`; each named pose as `<outfit>-pose-<name>.png`.

## 4. measure.txt

- **Proportions first.** `proportions:` lines give the built head, hand, foot and eye sizes and the eye line as body units and as shares of the head and of the figure; set them against the sheet's readings (`reading-a-reference.md`, Measure the hands, feet and face) through `targets` in the recipe or `targets=<targets-view.json>` on the build. Every `FLAG target` line (off by more than its tolerance, or unmeasurable, with the reason) is fixed before you judge a render by eye: hands, feet and faces built too small are caught here, not in a glance.
- `part ids`: every part's id and role, for `close=` and `only=`. `garment ... hem heights above the ground`: each hem's height, against the sheet's.
- `arm volume inside the torso`, `arm depth inside the torso as shown`: 0 at rest and near 0 as shown; more is an arm pressed into the body.
- `hip socket spread`: how far apart the legs' sockets sit; legs closer than their thighs are wide touch.
- `smallest gap between digit tips`: fingers or toes fused if near 0.
- `profile depth brow to chin`, `deepest notch`: how much the face's profile stands out.
- `face in shade` per view: above about 0.3 the face reads dark.
- `terminator ... blotches`: shading blotches; aim for none.
- `one surface ... open edges`: 0 is the aim; a few on small templated parts do not show, but read the close-ups of whatever changed since the count was 0.
- `join lines`: how much join ink is drawn; 0 on a body with `inkJoin: false` everywhere.
- `owned part vertices under cloth`, `owned parts' drift from rigid`: props a garment carries.
- `wardrobe:` lines: every garment's slot, layer, fit, covered parts with u and v spans, changes, holes and owned parts, and each outfit's picks. Check them against the clothes on the feature list.
- `poke-through <inner> through <outer>` per outfit, and per outfit in each named pose: `poke-through none` is the aim (`clothing.md`).

## 5. Walk the feature list

Go down `"x-features"` line by line. For each feature say which render shows it, whether it matches, and **name the recipe entry that makes it** (`ears: params.cup 0.35, areas.inner`; `eyes: look.irisRatio 2.2`; `tee: areas.rib`). A feature with no entry is not done; a feature whose entry exists but does not show is a bug to chase before adding anything else. Report the list with its entries when you finish.

## 6. The common failures

Check each one, every character:

| failure | looks like | fix |
| --- | --- | --- |
| eyes reading as stuck-on discs | flat stickers with a hard rim, proud of the face, staring from the side | `head-and-face.md`, Eyes |
| wobbly ear rims in three-quarter | the cup's lip kinked or rippled | fewer, smoother outline places; `rim` toward 0.25 (`ears-tails-extras.md`) |
| horn-like tufts | tufts or locks standing stiff and pointed off the head | shorter, wider at the root, leaned along the surface |
| ink seams at the hips | a line round each thigh's root or the neck's base on one-colour fur | `inkJoin: false` on that part (`body.md`) |
| edge-on accessories | a badge, brim or lens seen as a line from the front | its rest and roll (`accessories.md`) |
| missing neck | the head sunk into the shoulders, the chin on the chest | a neck part (`body.md`) |
| boxy clothes | a tee like a box, shorts like a skirt | shape the body under them; fit, `partFits` (`clothing.md`) |
| wobbly or angular body sides | the torso's side rippling in and out, a corner at the hip or waist, a flat shelf at the shoulder | fewer stations, each radius only growing or shrinking between features (`body.md`, Smooth profiles) |
| clothing copied into the body | a bare body with a dropped shoulder, a waistband pinch or a ledge at the hip | infer the anatomy under the clothes (`body.md`, Smooth profiles) |
| constant-width kneeless legs | pipes from hip to ankle | narrow the knee and ankle, swell the calf at the back (`body.md`) |
| oversized hands | hands as long as the face is high | `size` from the sheet (`hands.md`) |
| floating or sunk feet | soles off the ground or through it | the leg's length and end socket (`feet-and-paws.md`) |
| touching thighs | legs fused at the crotch, trousers one tube | the hips' width and the leg sockets' v (`body.md`) |

## 7. When to stop

Stop when every feature shows and matches, the common failures are absent, and the IoU through a fitted camera stays flat under further changes to proportion. Then report: the render paths, the feature list with entries, what still differs from the sheet and why (a limit named in a topic file, or a gap logged).
