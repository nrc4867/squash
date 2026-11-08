# Stage 7: digit shape

A working note for the branch that turns combs of tubes into hands, paws and feet. Folded into the HLD or deleted when it lands.

## Goal

Hands, paws and feet read as tubes: every digit is a round tube of one thickness, meshed on its own and overlapped on a palm, with no web between neighbours and nothing where a finger meets the palm. Two things fix most of it without research: a sheet spanned between the roots of neighbouring digits, the membrane block the data model already lists, and per-digit shape, a flattened section, a taper toward the tip and a knuckle at each joint. The implicit blend, fingers merging into the palm with a true fillet, stays the research item after this.

## What the design already holds

`docs/hld/data-model.md` lists a `membrane` block, a surface spanned between digits or parts, as one sealed subclass whose `build` returns a patch open in v or closed with poles; anchors, marks, weights and lines already address patches by name, u and v. The toe row builds a closed tube per digit, patch `name:i`, whose rings carry u along the part's axis, with a `flare` taller at the root, and since stage 6 a chain of joints per digit; the rig weighs a vertex by its patch and u. `parts.md` plans `wing-membrane` as an arm chain, a toe row of long fingers and a membrane spanning between the digits and back to a body socket.

What it does not hold: a patch whose vertices follow two bones chains at once. Every vertex today takes its bones from one patch at one u.

## Decided

- **A membrane is a closed sheet spanned between two rails.** `MembraneBuilder` in `geom` takes two swept patches and a u range on each, the rails, and builds a patch whose rings run from the rails' roots (t = 0) to the web's free edge (t = 1) and whose columns go along one face from the first rail's middle to the second's and back along the other face, closed in v. Its ends in v sit inside the rails and its root inside whatever the rails grow from, so only the web between shows; its free edge rounds off over the last rings to a thin lip. Fixed topology: `rings` by `segments`, the template's.
- **Its thickness is a fillet.** Half way between the rails the web is `thickness` of the rails' own half-height; toward each rail it swells to nearly the rail's own height inside it, so it leaves each digit's side with a fillet and fills the valley between two touching fingers above the palm. The free edge dips toward the middle by `sag` of the web's reach, the crotch between two fingers.
- **Each vertex rides both rails.** A membrane's patch carries `Spanned`: for every vertex, a u on each rail and the share of the way from the first to the second, smoothed so the web near a digit moves with it. The rig weighs such a vertex as the blend of what each rail's patch gives at its u, so the web follows the bones of both neighbours, the palm's near its root and each digit's joints up its sides, and a curl or a spread stretches it between them rather than tearing it off one. The seam band, the mesh's bone tags and a joint's rest bend read the vertex's own u, the blend of the two rails' u. A rail need not be a digit: any swept patch of the part serves, which is how `wing-membrane` will span a long finger and the arm.
- **The `membrane` block** spans the digits of a toe row listed before it: `on` (the row), `reach` (how far up the digits its edge meets them, a share of each digit's span from its root), `sag`, `thickness`, `rings`, `segments`, and `between`, the pairs of digits it spans, by default each digit with the next, leaving out a digit turned back. A pair may set its own `reach`, the thumb's web reaching further. Patches `name:a`, a pair named by its first digit. An area with `patch` set to the membrane's name colours it with the digits.
- **Per-digit shape on the toe row.** `section`, a named profile or control points for the digits in place of the circle; `width`, the digit's width as a multiple of its thickness; `taper`, its thickness at the tip as a share of its root's; `knuckle`, a swelling at each of its joints as a fraction of its thickness, over a short span either side. `flare` stays the knuckle where the digit leaves the palm. All four default to today's build. A drawn Side's heights and a drawn Top's widths are kept, as `flare` keeps them; `section` shapes drawn digits too.
- **A `webs` flag on each template** that has one switches the membrane off, so a part can go back to separate digits; it shows in the Shape inspector as any template flag does, by its name, with no new strings.
- **Templates.** `hand-3`: fingers on an egg section a little wider than thick, tapering to 0.8, knuckles at the joints, webs between the fingers reaching a third of the way up and the thumb's half way. The palm keeps its shape. `paw-3`: toes on an egg section, tapering, webs up to the toe pads. `foot-bird-3`: toes flattened, tapering to a point, knuckles at each joint, short webs between the front toes and none to the hind toe. `foot-2`: toes flattened and tapering, webs between them. Earlier versions stay shipped, so a stored character keeps its look and bone count until Update from library; new characters and the example files take the new versions, so the fennec, person and toucan goldens change, deliberately.
- **The rig's tree is unchanged.** A membrane adds no joints; its patch is not a digit, so it never shows in Bones.

## Rows

| Row | Delivers |
| --- | --- |
| Geometry | `Spanned` on `Patch`, kept by every copy of a patch; `MembraneBuilder`; tests on mesh invariants and fixed topology |
| Model | The `membrane` block with its lint, `section`, `width`, `taper` and `knuckle` on the toe row, the schema regenerated; bone tags for spanned vertices |
| Rig | Weights and seam bands for spanned vertices; a test that a curled hand's web follows its digits without tearing or smearing |
| Templates | `hand-3`, `paw-3`, `foot-bird-3`, `foot-2`; character and rig goldens and the example files rewritten |
| Smoke | Close views of the person's hand at rest and curled, the fennec's paw and the toucan's foot, looked at before and after |

## Exit criteria

1. **Webs between digits.** The person's hand, the fennec's paw and the toucan's foot build a web between each pair of neighbouring digits (the hind toe has none), through the slice build, with the template's topology and mesh invariants holding on every web.
2. **Webs follow a curl.** At a whole curl on the person's hand each web follows the bones of exactly the two digits it spans, no vertex leaves them, and no web edge stretches or shrinks further than twice, or than its own digits' surface does under the same curl with a margin, the skinning's own at a bent knuckle.
3. **Digits are shaped.** Fingers and toes taper toward the tip, swell at their joints and sit on a flattened section, on the new versions only.
4. **Stored characters survive.** A character holding the earlier versions builds as before.
5. **They read as hands, paws and feet** in the close views, judged by eye.
6. **Tiers hold.** `./gradlew check` passes; goldens are regenerated on purpose.

## Out of scope

The implicit blend as displacement (a true fillet where a finger meets the palm, the research item), `wing-membrane` and a membrane back to a body socket, tip sockets and claws, a Spread control, webs on drawn digits shaped by the drawing.
