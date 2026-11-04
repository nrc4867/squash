# Look

The target is comic line work combined with cel shading, chosen per scene. The reference pages do five things:

- Line work carries the style: a thick dark outer outline, thinner interior lines, lines that taper at their ends.
- Flat color, little shading. The main depth cue is that far legs and the far ear are a darker tone.
- Fur is an outline shape. Chest ruffs, ear fluff and tail tips are jagged edges, not textures.
- Faces are graphic marks. Eyes, mouth lines, sweat drops and shock lines change freely between panels.
- Only characters are outlined. Backgrounds are painted, cool and soft, so warm outlined characters stand out.

A plain 3D toon shader gets the fills and an outline. It cannot do fur edges, faces or tapered interior lines. So every character has two layers:

1. **A 3D body underneath.** Solid colored shapes that are posed, lit and depth-sorted. This is what lets you turn a leg and see the underside of the paw.
2. **2D marks on top.** Fur strokes, interior lines, eyes and mouth, drawn as strokes on the body's surface. A mark can have versions for a few angles that switch as the view turns. They follow the pose and stay readable from the camera.

## Shading

Chosen per object, within limits the scene sets. Flat characters in a shaded world is the house look.

| Mode | Look | Depth cue |
| --- | --- | --- |
| Flat | One color per part | Far limbs darkened, outline weight |
| Two-tone | Lit color and one shadow color, hard edge | Light direction, plus outline weight |

In two-tone, each part's shadow falls as a clean drawn shape: the shader reads each slice's proxy normal (the normal of its ellipse, blended along the axis) instead of the mesh normal, which is the edited-normals technique with no hand editing. Automatic from the light; no painted shadows, no lookup image.

Far parts darken automatically by side: paired parts on the side facing away from the camera get a darker shade of their color, swapping as the character turns. Subtle in the references, so it can be switched off.

**Base color first.** A character has a palette of named slots (fur, cream, pads, nose, inner ear, ink by default, from the body plan), and every part carries a base slot, so blocking out the character is choosing a slot per part before any painting: Fill on a part sets its base slot, and the Parts tree shows each part's base color in its row swatch. Template blocks with a `color` field (pads, nose leather, inner ear) name a slot, so they are right as soon as the slot is. Editing a slot recolors everything that names it. Mirrored pairs share base slots like everything else. The default palette is the body plan's, not a constant in code.

No image textures. Color is painted in sections, and nothing about it depends on the mesh, so no one thinks about vertices:

- **Fill.** The bucket colors a whole section in one click. A section is a part, an area its template names (a paw's pads, an ear's inside), or the closed shape a region border makes. The section stores a palette slot.
- **Brush.** A section can also be partly painted. A brush stroke is stored in anchor space with its slot and keeps its own edge, however few rings or segments lie under it.
- **Region border.** A curve drawn on the surface makes a crisp edge and a new section to fill. Each border can be inked or not.

Sections layer in a fixed order: the part's base slot, then filled areas, then regions, then brush strokes in the order drawn. Paint is rendered from a per-patch color grid in u and v at a fixed resolution, rebuilt from the stored strokes, with region borders evaluated per pixel so they stay crisp and never follow triangle edges. Anchors, areas and slots are the only stored form.

## Lines

Both modes share the same line rules: thick on the silhouette, thin inside, a line wherever one part overlaps another. The drawn width of an outline means nothing; line rules set rendered weight, and a width tool adjusts it along an outline afterwards. A line-weight adjustment lives on the body surface, so wherever that area is on the silhouette, from any angle, it uses the adjusted weight.

**Where parts meet.** Parts are separate meshes that overlap at their sockets. In flat shading the overlap is invisible, but in two-tone the shadow edge and the silhouette detector both see the crease. So the border at a socket is inked by default, which is also how the reference draws a leg against a body. Switching that ink off is tested in both modes in spike 3.

## Marks

Fur is mainly strokes drawn on the surface, shown where the area nears the silhouette; big shapes such as a tail tip are drawn into the outline. Graftals' rule for keeping tufts evenly spaced on screen may be reused.

Face marks are painted on the head's surface, with versions drawn for a few head angles that switch, not blend, as the head turns. Each expression has its own set of angle versions. Each mark's 3D position comes from the body it sits on, never estimated from the drawings. Marks anchor to template coordinates (see [data-model.md](data-model.md), Surface anchor), so they survive refits and oversketches.

## Eyes and gaze

Eyes carry the acting, so they are not loose marks. An eye is a **mark group** with named roles and controls, and the pupils have a movement system of their own.

**Roles.** Lid outline, white, iris, pupil, highlight, brow, lashes. Each role has its own ink and fill rule and a fixed draw order. The lid outline clips the white, iris and pupil. The highlight is the one mark that is camera-locked: it sits where the light and camera put it, not where it was drawn.

**Controls per eye.** Open (0 closed to 1 open, a morph between lid versions with the same control-point count, so blinks and sleepy looks are a value, not a drawing), expression shape (a blend within the expression set for lids and brows; angle versions still switch), pupil dilation, and a gaze offset. Every control is per eye, so one brow can rise alone.

**Two eye kinds.** A surface eye is a mark group on the skull's eye region, the area the template tags around the eye landmark. A bulging eye is a sphere part on the eye socket, with the same mark group painted on it; gaze is then a rotation of the sphere and lids are marks or small shells on it. Bulging eyes are first version, because they are also the easy path to correct gaze.

**The pupil system.** A `Gaze` on each character in a shot, keyed over time like a pose:

- **Target.** A point in the scene, the camera, an anchor on another character (its eye landmark, so two characters look at each other), or free, where each eye is placed by hand.
- **Solve.** For a bulging eye, the sphere rotates to the target within its travel limits. For a surface eye, the direction from the eye centre to the target becomes a 2D offset in the eye region's own u and v, scaled by the eye's travel, clamped to the lid shape with a margin, and clipped by the lid. Near targets converge both eyes up to a convergence limit.
- **Cheats, switchable per character.** A camera bias pulls both pupils a fraction toward the camera so they stay visible and reading at three-quarter views. Size compensation keeps the far pupil the same apparent size as the near one. A pupil that meets a lid can flatten against it instead of hiding.
- **Shape.** Round, slit, dot or oval per character, with dilation from a control or an expression.
- **In motion.** Eyes lead the head by a few frames when the target moves, an optional blink fires on a large gaze change, and angle versions switch with hysteresis on the character's timing (on twos if the character is), never mid-frame on a threshold.

**UI.** A gaze gizmo in the Shot viewport (a target handle, with lock-to-camera and lock-to-character), per-eye nudges and open sliders in the Face tool, and the expression picker. None of this is on a board yet: a Shot board for eyes is due before the posing screens are built.

## Mouth

The mouth is a mark group like the eye, with roles: lip line, inner, teeth, tongue, corners. Its controls are open, which drives the jaw joint and a lip morph together so the line parts as the jaw drops; expression shape (smile, frown, o, grimace), a morph within the set; and width. The lip line is drawn on the muzzle's surface and wraps around it, so it reads from the side as well as the front. The inner region is the skull template's inner area with its own color, revealed as the jaw opens; teeth and tongue are marks on it by default and parts later. Smiles and puffed cheeks move the muzzle, not the mark: they are pose-driven shape fixes on the muzzle keyed to the open and expression controls. Spike 9 includes a closed, half-open and open mouth at the same three angles as the eyes.

## Surface work rules

- **Expressions carry poses.** An expression set holds marks and a small face pose: ear joints, jaw, and brows where they are boned. Ears back is an expression, not a hand pose. Sets blend where control counts match and switch on the character's timing where they do not.
- **Fur has a grain.** Each part has a grain field, by default along its axis from socket to tip and outward on a ruff. A comb tool overrides it, stored as anchor-space vectors on the part. Fur strokes, outline tufts and later hair follow the grain.
- **Paint and regions cross parts.** A brush stroke or a region border that crosses a socket continues onto the parent patch; a region is a set of anchors on any number of patches. A face mask runs from muzzle to skull and a sock from paw to leg.
- **Paired parts mirror surface work.** Paint, regions and marks on a paired part mirror to its twin by default, with per-side overrides on top, the same rule as outlines.
- **Strokes can leave the surface.** A mark whose anchors carry an offset renders as a 3D line off the surface and moves with its part: whiskers, lashes, drips.
- **Any mark can be camera-locked.** The eye highlight rule applies to a wet nose, a tooth glint and a claw tip.
- **Effect marks belong to the shot.** Sweat drops, shock lines, blush, speed lines and tears are keyed per shot and pinned to a landmark or drawn in camera space. They are not part of the character and do not travel with it to the library.

## World

Both a painted 2D backdrop for the distance and 3D sets and props for what characters touch or walk around. Shadows per scene: none, a flat blob under each character, or flat cast shadows from the scene's light. Only characters are outlined; whether props are inked is open.

## Export note

Outlines and 2D marks are drawn by this tool. A glTF export would carry the body meshes, flat colors and rig, with outlines as an optional inverted-hull mesh. Faces and fur marks need an export plan later. Not needed for the first version.
