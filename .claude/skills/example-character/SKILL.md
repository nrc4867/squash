---
name: example-character
description: Make or improve a Squash character from a reference sheet or a description, an example or a test model, dressed or not, by writing a TypeScript character file on the recipe kit (or a recipe JSON), building it with one command and judging its renders. Use before adding or reshaping anything under examples/ or recipes/, or when asked for a new test model.
---

# Make a character

A character is a **recipe**: what the character is in artist terms, written as one TypeScript file on the kit (`import ... from "squash-kit"`, anywhere on disk) or as `recipes/<name>.recipe.json`. One command checks it, builds it, renders it and measures it:

```
<squash>/tools/recipe.sh kit <anywhere>/<name>.ts
```

You write the file, run it, read every render, change one thing, run again. Prefer the kit: it typechecks against the recipe schema, gives a height plan, tube profiles, shapes, face places and garments as helpers, and reads the reference sheet for you.

## Hard rules

- **Build the character's intent.** The sheet says what the character is: its proportions in heads, its style, the features that make it who it is. A template says how a part is built so it poses well. Neither is the answer: don't trace the sheet and don't settle for the template's defaults. Read the intent, choose the proportions, and compromise where the drawing can't be a sound 3D part (a hand a few pixels long, a foot under a cuff, a sleeve's outline): the part should read as the sheet's and still pose cleanly.
- **No code reading.** Everything a builder needs is in this skill, `kit/README.md`, `docs/reference/`, `docs/hld/`, `schema/*.json`, `templates/*.json` and the recipes under `recipes/`. Opening a `.kt` file, or a kit source under `kit/src/`, to learn what is possible is a gap: stop, log it (below), and find another way.
- **Original models only.** Choose every outline and proportion yourself from the reference; never reshape another example's data or copy its numbers. Read other recipes and `kit/examples/mannequin.ts` for structure only. Template-free bodies (the `blank` plan, `tube`, `blob`, `sheet` parts) are fine. Names are generic or invented: never a real character, franchise or brand.
- **No throwaway worktrees.** A character file lives anywhere on disk; the kit builds it from there. A new shipped example is `recipes/<name>.recipe.json`, made in the task's own worktree.
- **One change a run.** Body first, bare, from every side; then the head and face; then hands and feet; then extras; then one garment at a time.
- **The bare body is the anatomy under the clothes, not their outline.** A sweater's dropped shoulder, a waistband's pinch, a hem's ledge at the hip or a rip at the knee belongs to its garment. Infer the body beneath, smoother and simpler than the clothed sheet, with every side one curve (`body.md`, Smooth profiles); the garments put the folds back on top.
- **Improve, don't restart.** When a character already has a build, change that file. Settings tuned in earlier rounds (hands, feet, face) are kept unless a render beside the sheet shows them worse.
- **Read every render the run prints**, and `measure.txt`, before the next change. Judge against the sheet, not against memory of it.
- **Every feature has an entry.** The feature list goes in the file as `"x-features"`; when you finish, each feature names the recipe entry that makes it (`judging.md`).

## The workflow

1. **Read the sheet** (`reading-a-reference.md`). `tools/recipe.sh kit sheet views` finds the views; `grid` and `measure` give heights in heads and body units; `palette` the colours; `profiles` tube radii; `zoom` and `trace` the head, hands, feet, ears and tail. Write the feature list: proportions, every part's silhouette, every colour region, the eyes, mouth and marks, digits, extras, every garment.
2. **Plan the heights** (`body.md`). A `bodyPlan` of landmark heights: crown, chin, neck, shoulder, chest, waist, hip, crotch, knee, ankle. Check the head's share of the height against the sheet's head count before any part.
3. **Lay out the body** (`body.md`). Draw the figure outline from the sheet's front and side, and the torso, a real neck, arms and legs as `figurePart`s on it from the plan: their widths, joins and roots are read off the fit; A-pose rest; the joints where the sheet puts them. Build bare, read `figure-front.png` and `figure-side.png` for misses, then front, three-quarter, side, back and top.
4. **Head and face** (`head-and-face.md`). Head shape and size first, then the colour masks, then eyes, nose, mouth, brows.
5. **Hands, feet and paws** (`hands.md`, `feet-and-paws.md`). Measure their size in heads and read their intent from zoomed crops; build them from the templates' parameters, drawing outlines only where the sheet shows the part large and clear; check them as 3D parts (palm thinner than the wrist, feet flat), then judge them beside the sheet's crops at rest, in the display pose and in the T-pose.
6. **Ears, tails and extras** (`ears-tails-extras.md`). Cupped ears, tails, hair, horns, fins, wings, fur.
7. **Colour and marks** (`colour-and-marks.md`). Palette from the sheet, regions, marks, fur.
8. **Clothes** (`clothing.md`), then **accessories** (`accessories.md`). One garment a run; check `measure.txt`'s wardrobe and poke-through lines.
9. **Judge** (`judging.md`). Matched views beside the sheet, a reference camera fit, close-ups by part id, the feature list walked entry by entry, the common failures checked one by one.
10. **Log the gaps** (below), and report the render paths, the feature list with its entries, and what still differs from the sheet.

## Topic files

Read the one for the step you are on; each links the reference tables rather than copying them.

| file | read when |
| --- | --- |
| `tools.md` | first: the commands, their options and outputs, probe, watch, export, the recipe file's shape and the reference files |
| `reading-a-reference.md` | before any part: reading a sheet's views, heights, colours, profiles and outlines |
| `body.md` | planning heights and laying out the torso, neck, limbs, joints and poses |
| `head-and-face.md` | the head's shape, face masks, eyes, nose, mouth, brows, expressions, hair |
| `hands.md` | hands on arms, fingers, palms, nails, hand poses |
| `feet-and-paws.md` | feet, paws, toes, claws, pads, standing on the ground |
| `ears-tails-extras.md` | ears, tails, horns, spikes, fins, wings, tufts and fur |
| `colour-and-marks.md` | the palette, base colours, areas, regions, marks, fur colour |
| `clothing.md` | garments: slots, outfits, painted cloth areas, holes, tucks, fit, prints, changes |
| `accessories.md` | rigid things a garment carries: shades, buttons, buckles, heels, studs, brims |
| `judging.md` | after every build, and before calling the character done |

## Tooling gaps: keep a log

Keep `recipe-gaps.md` beside your character file, or in your scratch folder for a recipe under `recipes/`; it is never committed. Add a line every time you had to read code, or guess and render to find something out: the file or the trial, the fact you needed, and the tool or reference that would have told you. Reading `docs/reference/`, `docs/hld/`, `schema/`, `templates/`, `kit/README.md` and other recipes is not a gap; a fact one of them should hold and does not is. Report the log's lines when you finish.
