# Spike: the implicit blend as displacement

A working note for the `blend-spike` branch: fillets where a part's patches cross, and where a part comes out of the part it sits on, grown by moving the vertices near the crossing onto a blended implicit surface, so every grid, every slice and every (u, v) stays. Folded into the HLD or deleted when it lands.

## Papers read

Read from the papers' own text, from the authors' preprints; no research code was opened.

- **Implicit Skinning: Real-Time Skin Deformation with Contact Modeling** (Vaillant, Barthe, Guennebaud, Cani, Rohmer, Wyvill, Gourmel, Paulin; ACM TOG 32(4), 2013). Each bone's piece of the mesh is approximated by a smooth field: a Hermite radial basis function on the kernel r³, fitted to about fifty points spread over the piece with their normals, plus two points on the bone's line to close the holes at the joints. The resulting distance-like field is remapped by a fifth-degree polynomial to a compactly supported field in [0, 1], 0.5 on the surface, reaching 1 only on the bone. Neighbouring bones' fields are joined pairwise by a composition operator (union, a gradient-based blend, or a gradient-based bulge in contact) and the pairs by max. Each vertex keeps the field value it has at rest, its own iso-value, so the mesh's detail is never lost to the smooth field. Per frame the mesh is skinned by dual quaternions, the fields are moved rigidly with their bones, and each vertex steps along the gradient by Newton steps (damped by 0.35) back to its own iso-value, stopping where the gradient turns by more than 55° between steps, which marks a contact surface. Tangential relaxation (mean value coordinates on the one-ring) is interleaved with the steps, and a diffused Laplacian smooth is applied around contacts afterward. Fields are sampled into 32³ textures and operators into 128³, evaluated on the GPU.
- **Robust Iso-Surface Tracking for Interactive Character Skinning** (Vaillant, Guennebaud, Barthe, Wyvill, Cani; ACM TOG 33(6), 2014). The same fields, but the mesh tracks the iso-surfaces from the previous frame instead of restarting from the skinned pose, which lets deep bends and contacts resolve; it adds operators that combine blending with local self-contact, and an as-rigid-as-possible tangential relaxation for skin that slides.
- **A Gradient-Based Implicit Blend** (Gourmel, Barthe, Cani, Wyvill, Bernhardt, Paulin, Grasberger; ACM TOG 32(2), 2013). A composition operator that reads the angle between the two fields' gradients as well as their values. An *opening function* maps that angle to the opening of a blend: closed (a clean union) where the gradients align, so surfaces lying together do not bulge and thin details are not swallowed; open (a blend) where they cross square; closed again where they oppose, so shapes facing each other across a gap do not blend at a distance and holes keep their topology. Their operators are built quasi-C∞ from boundary curves and silhouette curves in the (f1, f2) plane, with no closed form, and are precomputed into textures. The background section also states the functional blend of Barthe et al., min(f1, f2) − H(|f1 − f2|), with H a piecewise polynomial setting the blend's shape.
- **Sketch-Based Implicit Blending** (Angles, Tarini, Wyvill, Barthe, Tagliasacchi; ACM TOG 36(6), 2017), for its summary of the operator family: an operator is a function of (f1, f2, θ) on [0, 1]² × [0, π], unions with g = 1 when either is 1 and 0 when both are 0, and the inside f > 0.5 convention with linear fall-off fields.

Convolution surfaces (a kernel integrated along a skeleton) are the other skeleton-based field: they blend smoothly by sum, but a sum bulges where pieces overlap, which is exactly the overlap Squash's parts have at every socket and digit root. The gradient-based family exists to fix that, so the spike follows it.

## What suits Squash

Squash's parts are separate swept patches on a fixed grid that overlap: digits sunk into a palm, a web whose ends sit inside two digits, a leg's open end deep in a torso. Every anchor, mark, weight and line addresses a vertex by patch name, u and v. A blend that remeshes would break all of it. Implicit Skinning's projection does not: it moves existing vertices along a field's gradient and never changes connectivity. So the spike takes three things from the papers and leaves the rest:

- **Per-patch fields, from Implicit Skinning.** A Hermite RBF on r³ with a linear part, fitted to about 48 samples spread over each patch's grid with outward normals from the grid (a pole once). It is kept as the distance-like field (negative inside), without the remap to compact support: the blend below needs distances, and the patch's box widened by the fillet radius bounds where a field is read. The fit is one dense solve of 4m + 4 unknowns.
- **Each vertex's own level, from Implicit Skinning.** A vertex steps by Newton's method along the joined field's gradient until the joined field equals what the plain union of the fields gave at its rest place. A vertex on the surface lands on the fillet; one away from every crossing has union and joined fields equal and does not move at all; the fitting error of a field cancels, because the vertex keeps its own offset.
- **The opening by gradient angle, from Gourmel et al.** The blend is shut below 15° between gradients (surfaces running alongside: no bulge), wide open from 60° to 100° (a finger leaving a palm), and shuts again by 140° down to a share `apart` (surfaces facing across a gap: no blending at a distance).

## The field and the operator chosen

`GradientBlend` joins two signed distance fields a and b with radius k as min(a, b) − k·h²/4, h = max(0, 1 − |a − b| / k), with k scaled by the opening. It is the functional blend's form with a quadratic H: the plain union wherever the fields differ by k or more, so the far parts of both surfaces are untouched exactly, and the surface grows by at most k/4 where it crosses. Its gradient is the mix (1 − h/2)·∇a + (h/2)·∇b toward the leading field; the opening's own gradient is left out of the step, which slows Newton near the opening's edges but does not move its root. Several patches join as the least of the single fields and of every joined pair, Implicit Skinning's union of pairs.

Two choices keep the lines clean:

- **One side moves.** When both patches at a crossing move onto the fillet, both lie on the same surface there, and the lines draw scribbles where two coincident surfaces fight. A `still` patch (the palm, the parent at a socket) lends its field and keeps its place; the moving patch covers the whole fillet, a vertex of it buried in the still patch rising toward the fillet as far as the blend lowers the field there, so the moving surface sweeps out over the still one and dives back under it beyond the fillet's reach.
- **Only crossings that are not fillets already.** Joining a web to its digits draws contour loops where the web's own swell meets the digit at a grazing angle; joining two digits merges a hand into a mitten and moves the fingertips. The template names the pairs it joins.

Shading normals turn toward the joined field's gradient by the share the blend lowered the field, so two-tone shading follows the fillet.

## What is built

- `geom/blend`: `HermiteField` (fit and evaluation with gradient), `PatchSamples`, `GradientBlend`, `ImplicitBlend` (fields per patch, Newton steps capped at twice the fillet's reach, shading normals turned), `solveDense`.
- A `blend` block on a template: `radius` (a share of the axis length), `apart`, `between` (the pairs of patches it joins, a repeating block's name covering its patches, every pair when left out), `still` (patches that keep their place), `socket` (also fillet where the part comes out of its parent), and `if`. No shipped template has one, so nothing's look changes.
- `partBlendNode` follows the slice row in the session: `PartBuild` carries the block's `BlendSpec`, and the node applies it before the mesh. `surfaceOf`, for planned parts and `assembleNow`, applies it too.
- `socketFilletNode` follows the assembly: for each part whose blend block says `socket`, its patches and its parent's are placed in body space (fields fitted to samples taken in the part's own space, so a mirrored part's normals stay outward), the part's vertices near the crossing move, the parent's stay, and the part's surface is rebuilt from its moved patches. The rig, the lines, the render and the posed body all read the assembly, so they see the fillet; surface work resolves against the part's own unfilleted surface, so a mark within the fillet's reach of a socket sits up to k/4 under it.
- Tests: `ImplicitBlendTest` (a field's sign and gradient, a T-crossing's fillet with the far ends untouched and the move bounded, no blend across a gap, only joined patches move, a still patch's partner rises over it), `ImplicitBlendBuildTest` (a test-only `hand` 3 with a blend block: grids, slices and mesh invariants hold, fingertips, palm and webs stay), `SocketFilletsTest` (a test-only `leg` with `socket`: the torso is untouched, the leg's top flares, its lower half stays). Slow: `BlendCloseUpSmokeTest` and `BlendBenchTest`.

## Measurements

`BlendBenchTest`, the test-only blended hand, paw and leg against the shipped ones, this machine, averaged over five runs after one warm-up:

| Step | Person | Fennec |
| --- | --- | --- |
| One hand or paw, slices alone | 0.28 ms | 0.3 to 0.9 ms |
| One hand or paw, slices and blend | 9.6 to 10.8 ms | 5.7 to 6.8 ms |
| A leg, slices alone and with its (socket-only) block | 0.2 ms both | 0.2 to 0.4 ms both |
| Socket fillets for the whole character (legs) | 27 ms | 45 ms |
| Whole session built and settled, off / on | 0.63 s / 0.87 s | 0.97 s / 0.95 s |

The whole-session numbers are noisy (the fennec built faster with the blend on); the step times are the honest measure. Per character the blend adds about 20 ms for two hands and 27 to 45 ms for four socket fillets, nearly all of it fitting fields (one dense solve of about 200 unknowns a patch, the torso refitted for every leg) and Newton steps over the vertices within reach.

## On and off, read from the close views

Images: `app/build/smoke-images/blend-*-{rest,curled}-{off,on}.png`, six views each.

- **Hand, at rest.** The thumb is the clear gain: off, it meets the palm's side at a notch; on, its root swells into the palm with a soft fillet, visible from the back, the palm side and from below. The finger roots along the palm's end are about the same: the short crease ticks where each web crosses its fingers are still there, a little shorter in some views and as busy in others. The palm is still a rounded box; the blend only acts where patches cross, so it cannot round the palm's own shape. The fingers still sit side by side as separate tubes, deliberately: joining digit with digit filled the valleys between them from root to tip and read as a mitten.
- **Hand, curled.** Much like off. The fist's knuckle line shows a few more crease ticks where the curled fingers' filleted roots meet the webs; the thumb still reads better.
- **Paw.** Very little changes at this radius: the step where the toe block meets the paw's body is a touch softer in side view and its lip a little rounder from above; the underside and the pads look the same. The fennec's toes are drawn, so they are large against the body and the crossings are shallow.
- **Leg under the torso.** The clearest gain: off, the leg is a straight tube that stops at the torso's skin; on, it flares out over the last part of its length and meets the torso with a fillet in every side view, reading as a limb growing from the body. The torso keeps its shape and shading exactly. The inked border at the socket is still drawn along the fillet's foot, and from the front a small corner of the flare shows at the torso's outline.

## Verdict

- **Boxy palm: not fixed.** A fillet only acts at crossings; the palm's box section needs a palm shaped differently (a section, a cupped palm, a palm that narrows into the wrist), not a blend.
- **Finger roots: partly.** The thumb's root is fixed well. The four fingers' roots get a small fillet into the palm's end, but the crease ticks where the web meets the fingers stay; they come from the web's own surface crossing the digits at a grazing angle, and blending the web into the digits makes them worse, not better. A web built to meet its digits tangentially, or lines that skip grazing crossings, is the fix there.
- **Fingers merging along their length: possible but not wanted as built.** Joining neighbouring digits merges them; with the opening tuned to shut for facing surfaces it still filled the valley along the whole finger, because two overlapping tubes cross along their full length.
- **Leg joins: fixed** for a leg sunk into a torso, with the parent untouched.
- **Speed.** At rest only. 6 to 11 ms per hand or paw rebuild is affordable on a shape drag of that one part (the slice row reruns only for the part being drawn), but 27 to 45 ms for the socket fillets on every assembly change would be felt at stroke rate, since the assembly reruns for any part's edit. Caching each part's fields with its build, and refitting the parent once per character rather than per child, would take most of that away. Posing never reruns the blend: the fillet is built at rest and skinned with the part, so a bent finger carries its rest fillet.
- **Shipping.** Not on a new `hand` version: the hand does not clearly read better. The leg join does, and is the candidate for a shipped `leg` version once the socket ink at the fillet's foot is decided.

## What pose-time contact would need on top

The fields here are the right ones for Implicit Skinning; what it adds is per frame:

- Fields per bone rather than per patch, moved rigidly with their bones (a patch's field per bone span, or a patch's field moved by the bone owning its middle), with the remap to compact support so a field is read only near its bone.
- The skinned positions as the Newton steps' start, each vertex returning to its rest level, stopping where the gradient turns sharply (contact), and contact operators (the bulge in contact) between parts that touch only when posed.
- Tangential relaxation interleaved with the steps and a smoothing pass around contacts, which need each vertex's one-ring, which the fixed grid gives by arithmetic.
- Speed: the posed path reruns per frame of a drag. Fitting moves out of the loop (fields fit at rest and only move), leaving evaluation; at about 50 kernel terms a field and a few fields a vertex, the vertices within reach of a joint are a few hundred per joint, so a CPU pass is a few milliseconds per bent joint, sampled grids (as the paper does) bringing it lower.

## Out of scope

A shipped template with a blend block, a Shape inspector control for it, blend in the golden fixtures, marks following the socket fillet, and pose-time contact.
