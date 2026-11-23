# Sheet reader and proposers

A reference sheet is evidence, not an answer. The kit reads a sheet's views into silhouettes and hands them to a **proposer**, which proposes what they show in a form Squash builds from: for a creature, a bare body at rest as a figure outline and the recipe that fits it. The reader knows views, pixels, landmarks and what hides what; it knows nothing of bodies. The proposer knows its subject. Objects (a pen, a desk, a car) are to take a proposer of their own on the same reader. Everything here is in the kit: `kit/src/reader`, `kit/src/propose`, and the command `tools/recipe.sh kit sheet propose <readings.json>` (`kit/cli/sheet/propose.ts`).

## Readings

A sheet is described by a **readings file** (`squash-sheet-readings/1`, `kit/src/reader/readings.ts`): which proposer and plan read it, its views, the scale, the marks held by hand and the layers that hide the shape. The proposal is made again from it whenever it changes; the edit page saves it.

- **Views.** Front, side and top, each optional, each an image or a box of one, with its background colour and tolerance. Back and bottom mirror front and top, so they add no shape and are not read. A side view says which way the subject faces and a top view which way its front points; each is turned to face right and point up before it is read. A front or top view can be read from one half of the image, mirrored (`half`): a figure waving one arm is read from the side that hangs.
- **Scale.** Body units a pixel (`unitsPerPixel`) when the views share a known scale, else the subject's height in body units (its length, for one read from the top alone), else a default head.
- **Silhouettes** (`readSilhouette`). The pixels that differ from the background by more than the tolerance, the pieces large enough to be the subject, small enclosed gaps filled (an eye, a highlight). A view's probes are what a proposer measures with: runs across a row or down a column, a march from a place to the edge, the lowest row (the ground) and the column the subject is mirrored about.
- **Hiding layers.** A box on a view where something drawn over the shape hides it (clothing, fur, hair, a tuft, a tail), with its kind and its looseness: how far it stands off the shape, in the proposer's unit. The proposer reads the shape under it, and every reading inside it is inferred.
- **Held marks.** A landmark the user dragged, by id, view and pixel. The proposer pulls that landmark to it.

## Evidence

Every proposal returns its **evidence**: each landmark as a mark and each measurement as a span on the view it was read from, from where it starts to where it ends, with its value in the proposer's unit and its source.

| Source | Meaning | Drawn |
| --- | --- | --- |
| read | read off a given view | blue |
| held | placed by hand | green |
| inferred | read under a hiding layer, loosened by it | magenta |
| default | no given view shows it; the plan's value | amber, dashed |

`evidence-<view>.png` draws it over each view (`evidenceImage`), and `shape-<view>.png` lays what the proposer proposes over the view's silhouette: both grey, the view alone pale, the proposal alone red (`shapeImage`). A reading from the plan is shown where it applies, never explained: a depth the front view cannot see is drawn across its part, dashed.

## The edit page

`edit.html` is written beside the proposal; with `edit=true` the command serves it on 127.0.0.1, port 47842 unless `port=` says otherwise, clear of the recipe server's 47830 (`kit/src/reader/page`). Each view shows its sheet under its evidence. A landmark is dragged to hold it, and the server proposes again with it held; a layer's box is dragged by its corners, its kind and looseness typed, a layer added to a view; Release marks lets them all go. Save writes the held marks and the layers into the readings file and writes the proposal again. Opened as a file with no server, Save downloads them instead.

## The creature proposer

`creatureProposer` (`kit/src/propose/creature`) proposes a creature's bare body at rest, proportioned in heads, from whatever views are given.

### Body plans are data

The user picks the plan in the readings; the tool never guesses it. A plan (`squash-creature-plan/1`, `plans/*.plan.json`) gives:

- **Parts**: each a trunk (on the middle) or a limb (a mirrored pair), the part it sits on and where, its rest, outward and lean, its length, its joints with their limits, and its default radii at stations along it, by profile name (`left`, `front`, `back`, `up`, `down`, `outer`, `inner`), all in head units.
- **The rest pose** it stands in, as those rests: A-pose for the biped, a square stance for the quadruped (trunk level, legs straight down), wings folded and the trunk tilted up for the bird.
- **Ends**: the parts the figure leaves out (head, hands, feet, paws, beak, wings, a tail of feathers), each an ellipsoid in the fit, sitting on a part's tip or along it, and the template it becomes in the recipe with the template's own length, so its size follows the fit. An end can follow its part's direction (a hand), can be a pair of its own (wings), can hide the body under it (folded wings, tail feathers), or can shape the fit alone (a snout a template head carries).
- **Landmarks** by name on a part at a share along it: knee height for a biped, withers for a quadruped.
- **Default looseness** of each hiding layer kind, and the recipe's display pose and named poses.

`biped`, `quadruped` and `bird` ship. One head is the head end's height.

### Fitting the plan to the views

The plan's body is fitted to the views by model-based silhouette fitting (`fit.ts`), in the manner of fitting a parametric body to silhouettes from the sketch-based modelling and model-fitting literature:

- **The body** (`body.ts`) is the plan's parts swept along their chains as rings, round each section the quarter ellipse between the profiles either side, closed ends capped, ends as ellipsoids, a limb mirrored to its right side, the whole grounded on its lowest point. Its parameters (`params.ts`) are a scale, where the side and top views lie, each part's length, each profile's width at its root, middle and tip as a smooth scale curve, each end's size (and a head's place off its neck), and the pose the sheet shows: the root's pitch and each part's swing about the body's left axis, a limb's spread about its forward axis and its middle joint's bend. A turn is a parameter only when a given view can see it: a swing, pitch or bend only with a side view, a spread with a front or top view.
- **The cost** of a set of parameters is, for each view, the body rastered at the fit's size (`raster.ts`: each pair of neighbouring rings filled as their hull) against the view's silhouette shrunk to at most 180 pixels and padded all round, the hiding layers' boxes eroded by their looseness: 1.5 for each pixel of body where the view shows none, 1 for each pixel of view the body leaves bare, over the view's area. Body never lies outside the outline; ears, hair and tufts may stand off a body. Each parameter is held toward its plan value by a light pull, so what no view reads stays the plan's default, and each held mark pulls its landmark.
- **The search** moves one parameter at a time, each way by its step, keeping a move that lowers the cost and halving the step where neither does, in stages: place, pose, lengths and sizes first, then everything, then widths, sizes and lengths again.

### The proposal

From the fitted parameters the body is built again **at rest**, in the plan's rest pose with the fitted proportions; the sheet's own pose is evidence only.

- **Figure outline** (`outline.ts`). Each view's silhouette of the body at rest is drawn large, its joins closed into fillets, traced (`traceLoops`), and kept only where it bounds a part the figure fit reads in that view (`reads.ts`, the fit's own rules: a level trunk is read from the side and above, not from the front; hind legs behind front legs from the side alone; a spread limb not from the side). It is cut where an end stands over it and past the open tip of a part an end sits on, and, in the front and top views, at the middle; each run becomes a stroke of few places with its sharp corners, the figure on its right, an end on the middle `mirrored`.
- **Recipe** (`recipe.ts`): the plan's parts as figure parts on the `blank` plan, each limb's socket given the reach and v its root has at rest so the fit places nothing, each end its template sized to the fitted ellipsoid on a socket where its near edge meets its part's axis (a head on its neck at the chin), the figure drawn at rest, and the plan's display pose and poses.
- **Proposal** (`proposal.json`): the plan, the views read, the fit's cost, the proportions in heads (the head in body units, the height in heads, each landmark, each part's length and widths) and the evidence.

Build the recipe with `tools/recipe.sh <id>.recipe.json`; the figure fit (parts.md, Figure) shapes its tubes and writes `figure-front.png`, `figure-side.png` and, where the figure is drawn from above, `figure-top.png`.

### What it cannot do yet

- A segmented body with six legs (a bug) has no plan. A plan could give its segments as trunks on each other's ends and three limb pairs; what the engine lacks is a bent rest: a figure chain at rest is straight along its rest, so a leg that rises to its knee and falls to its foot can only be a pose, and the figure fit reads limbs from the top view not at all, where a bug's splayed legs show best.
- The sheet must be drawn in one pose for both halves unless one half is read (`half`); the fit mirrors every limb.

## Test sheets

`tools/recipe.sh <recipe> views=true` writes a built character's **flat sheet**: its silhouette at rest, black on white, straight from the front, its left and above at one scale, with `views.json` giving the scale and where the ground and the middle fall in each (`SheetViews`, in the app's recipe runner). Sheets of the public examples are under `kit/test/sheets/`, each with its readings, so a proposal's true answer is the example itself.
