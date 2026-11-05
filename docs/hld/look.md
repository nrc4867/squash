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

In two-tone, each part's shadow falls as a clean drawn shape: the shader reads each slice's proxy normal (the normal of its ellipse, blended along the axis) instead of the mesh normal, which is the edited-normals technique with no hand editing. Automatic from the light; no painted shadows, no lookup image. The slice build computes the proxy normal once, in the ring's proxy normal frame, and carries it to the mesh per vertex, so shading adds nothing to a drawn mesh beyond the build. A ring's slope along the axis turns its normals toward the axis, so a rounded end shades round. A patch with no slices, such as a pad, shades by its mesh normal.

The shadow tone is the slot's own: a palette slot can carry a shadow color, and with none its color shifts warm, blue falling fastest and red slowest, rather than multiplying down, since a pale slot multiplied turns grey. A head's skull and muzzle are one part with overlapping runs, so across the overlap their proxy normals blend and one shadow edge runs over both. A proxy normal reads the drawing's size, not a run's sinking inside another, so hiding a stretch does not turn the shading. Where a run covers another the covered one neither shows color nor fights it: the surface hands over at the middle of the overlap, the later run rising from inside to the drawing's size there and the earlier sinking inside right after, so one line divides them and either's color past that line lies out of sight. Marks take no shadow tone.

Far parts darken automatically by side: paired parts on the side facing away from the camera get a warm shift of their color and of their shadow, by 18%. It ramps in as the camera leaves head-on, from a fiftieth of the way off the body's plane to about a fifth, so an orbit passes from one side to the other with no step in tone. Subtle in the references, so it can be switched off. The choice of flat or two-tone and of far parts darker is stored on the character.

**Base color first.** A character has a palette of named slots (fur, cream, pads, nose, inner ear, ink by default, from the body plan), and every part carries a base slot, so blocking out the character is choosing a slot per part before any painting: Fill on a part sets its base slot, and the Parts tree shows each part's base color in its row swatch. Template blocks with a `color` field (pads, nose leather, inner ear) name a slot, so they are right as soon as the slot is. Editing a slot recolors everything that names it. Mirrored pairs share base slots like everything else. The default palette is the body plan's, not a constant in code.

No image textures. Color is painted in sections, and nothing about it depends on the mesh, so no one thinks about vertices:

- **Fill.** The bucket colors a whole section in one click. A section is a part, an area its template names (a paw's pads, an ear's inside, a chest, a tail's tip), or the closed shape a region border makes. The section stores a palette slot. A click fills what shows under it: a region, a filled area or a colored block, and otherwise the part. Set to Area, it also reaches an area the template names there that nothing has filled. The Color drawer lists the selected part's named sections by swatch.
- **Brush.** A section can also be partly painted. A brush stroke is stored in anchor space with its slot and softness and keeps its own edge, however few rings or segments lie under it. Softness runs from 0, a hard edge, to 1, a fade across the whole radius, for a cheek blush, a blend from fur into a cream belly or fur tips that fade.
- **Region border.** A curve drawn on the surface makes a crisp edge and a new section to fill. Each border can be inked or not.

Sections layer in a fixed order: the part's base slot, then filled areas, then regions, then brush strokes in the order drawn. Color is rendered as vector shapes cut to the mesh, with no color grid and no image or lookup texture at any resolution, so a hard edge stays crisp and a soft one smooth at any zoom, and neither follows a triangle edge. Anchors, areas and slots are the only stored form, and the shapes and their cuts are derived from them.

**Color is vector.** Every section and stroke is a shape in its patch's u and v: a brush stroke is its anchors and radius, swept to a closed outline, or with a softness a fade around them; a region is its border; a filled area is the template's rectangle; a part's base is the whole patch, drawn by the mesh itself.

- **Flattening.** Curves flatten to a quarter of a pixel at the largest view's scale, in doubling steps, and flatten again only when the zoom has moved a step up or two steps down, so there is no resolution setting.
- **Cutting.** Each shape is cut along the mesh's rings, lines of points and cell diagonals, so every triangle lies in one of the mesh's own triangles, and a vertex there is interpolated as the GPU interpolates it. The edges are the mesh's, not a smooth bilinear surface across the cell: a non-planar cell's bilinear surface lies off its two triangles by up to a few pixels and would float or sink. An anchor's own position is still `positionAt`'s bilinear one. A cell against a pole is one flat triangle in the mesh, so its whole square maps onto that triangle, with no diagonal. Filled means the non-zero rule, so a stroke crossing itself is one fill, and each vertex keeps its patch, u and v for skinning.
- **Where a shape reaches.** A shape wraps across the v seam of a closed patch and stops at the edges of an open one. A brush measures its radius along the surface cell by cell, so a flat cap's one wide cell counts for what it is, and adds a cap by distance in space when a pole is within reach. A border going once round a closed patch holds the side toward the last ring. A shape crossing a socket arrives as anchors naming the other part's patch and is cut there. A stretch of border on a part that crosses to its parent closes past the part's socket ring, so the shape holds what runs up to the socket; a stretch on the parent closes on itself, however near an end ring it lies. A stretch crossing to another patch of its own part closes past an end ring when both its ends lie near that ring. A brush keeps one width across a socket, as a share of the axis of the part that holds it. A mirrored side cuts the same u and v on its mirrored mesh.
- **Fading.** A soft stroke's opacity is a field over its patch: full within its core, which softness leaves at the radius times one less the softness and never under a thirty-second of the radius, nothing past its radius, and linear in the distance to its curve between. The distance is in space, so a seam or a pole needs no case, and it is held from reaching further around v than a hard brush's edge unless a pole is in reach, which the brush then covers. Each of the mesh's triangles the brush reaches is split along its own edges until opacity runs straight across each piece. Whether an edge splits depends on the edge alone, so neighbours meet vertex to vertex, and each place is covered once however the stroke crosses itself. A stroke going on onto another surface keeps its strength past that end by a radius, along its last direction, so no dip shows at a socket as long as its anchors either side of the crossing lie within a radius of it, which the tools' drawing holds by finding where the pointer crossed. Only the far ends of the whole stroke round off. Each vertex carries its opacity, so the fade is geometry with no lookup, the GPU interpolates it with no steps at any zoom, and the blend runs on the surface's own values, so it is linear in sRGB. It does not depend on the zoom, except that a fade narrower than twice the flattening tolerance is a hard edge until a closer zoom resolves it.
- **Layers.** Later layers overdraw earlier ones in the order above, and solid same-slot neighbours on one patch merge. A soft stroke stays its own layer, so where fades overlap they build up to full color and a hard stroke over a soft one reads by order. A soft layer tones like a hard one, so the shadow edge is one line through it. A layer writes no depth and shows only faces toward the camera. It sits a sliver of a pixel toward the camera, more where the surface turns away and never under a few steps of the depth buffer, so it cannot z-fight its mesh at any orbit and fails against anything truly in front of it.
- **Slots.** A slot resolves to a color at draw time, so a palette change cuts nothing. A base slot the palette does not hold, or none, shows the look's own part color; a layer whose slot the palette does not hold is not drawn, so what is under it shows.

## Lines

Both modes share the same line rules: thick on the silhouette, thin inside, a line wherever one part overlaps another. The drawn width of an outline means nothing; line rules set rendered weight, and a width tool adjusts it along an outline afterwards. A line-weight adjustment lives on the body surface, so wherever that area is on the silhouette, from any angle, it uses the adjusted weight.

**Where parts meet.** Parts are separate meshes that overlap at their sockets. In flat shading the overlap is invisible, but in two-tone the shadow edge and the silhouette detector both see the crease. So the border at a socket is inked by default, which is also how the reference draws a leg against a body. Switching that ink off is tested in both modes in spike 3. The crease is the curve on the part where it leaves its parent, found on the part's grid, its steps from one grid edge to the next calmed along the surface, and draws as an even ribbon. A graph node finds it, so it follows the assembly and a surface edit does not find it again.

**The two weights.** Every part's contour draws as one ribbon twice: in the back slice of the depth buffer, where only what clears the whole figure shows it, and in the scene at the interior weight, so a part standing clear of another has the thin line. The thin line lies half inside the edge and the thick one shows only outside it, so together they measure the silhouette weight across. A selection in a locked pane does the same in the depth just behind and just inside the front slice, so it reads over what is ghosted around it. Pads are built with enough segments that a nose is not a polygon.

**The contour.** A contour runs along the mesh's edges between a triangle facing the viewer and one facing away, counting the faces that close a patch's end rings, so it lies on the edge the fill shows, sawteeth included, and no fill pokes past its line. Smooth normals or smoothing the run would pull it inside a ridge, burying the line. The ribbon's own curve through those points takes off the corners of the rings, with the points held a couple of pixels apart on screen, since a stretch running along the view would otherwise leave the ribbon no direction. A connected piece thins with its length on screen below a short reach, so a small one grows from nothing rather than popping. A contour is camera work, so the frame finds it from the graph's grids, once per camera that moves.

**Line rules and line weights.** Silhouette, interior and taper are the character's, set by command; a mark with no width of its own narrows over its share of taper at each end. A line weight is a stroke of anchors on the surface with a radius and an amount. Every line crossing it, contour, crease or mark, takes the added weight at full strength within most of the radius, easing to none at its edge, and it reaches only the parts its anchors name.

## Marks

Fur is tufts standing on the surface, which show where they break the silhouette. A fur stroke is its own kind of mark, a root on the surface and nothing else: the grain says which way it runs, and the part's fur rule how it shows.

An inked line along the grain reads as a loose hair against a thick silhouette, so a fur stroke is a tapered wedge filled in the slot under it, with ink on its edge, whose tip breaks the silhouette. A sawtooth drawn into an outline is swept round the part as a stack of ridges, which reads as tufts only side on and as a segmented body at three-quarter, so an outline carries a part's mass and its tufts are these strokes.

- **Which tufts stand.** A part's fur rule holds spacing and length. Neither the camera nor zoom changes which tufts a part has or their size: only the rule does. Each root has a room on the body, worked out once with the camera aside: the first root in an order seeded from its id has all of it, and each next is the one furthest from those taken, by that distance. A tuft stands when its room reaches the spacing, starting small at 0.7 of the spacing and reaching full size at the spacing, its ink with it, so tufts lie the spacing apart on the body. The part's surface hides all of a tuft but what clears the rim, so it rises over the edge as the body turns and sets behind it.
- **Its length.** Length and spacing are set in pixels as a view of 280 pixels to a body unit shows them, and hold on the body, so a tuft keeps its size against the part at every zoom and in every pane. Each tuft's length varies a little by its id, so a row does not read as a saw.
- **Its shape.** A tuft is a fixed shape on the body: a blade from a base sunk into the surface to its tip, along the grain and rising from the surface, bowed slightly away from it. Its tip shows where that fixed point shows from every camera, so an orbit shows it turn with the body and never grow or swim. Only the way its width faces reads the camera: its width turns to the viewer, narrowing to a point as the tuft runs straight at them. Its fill takes the tones of the slot under its root, read from the part's own brush strokes, regions, filled areas and base in that order, with the normal at its root, so a two-tone shadow carries onto it. Every pane fills it from the palette at that pane's shading, as the part under it. A part's ink is one ribbon holding every edge, drawn before its fills, twice the width of the figure's contour with the fills covering the inner half. So strokes that overlap read as one jagged shape with one outline, and that outline stands in for the contour near the silhouette. Parts draw their fur furthest first.
- **Its depth.** A wedge sits back along its line of sight, just before the rim that makes the part's outline in the way it runs: the part is taken as balls along its length, and of those the wedge's root could cross, the one the line outward from the root leaves last, the rim of its own ring, or a ball that covers its tip, whichever lies furthest back. So that part's surface covers all of it but what clears the part's outline, from any angle, whichever ring it roots on, and no fur shows on the part's face. A wedge covers both weights of the contour where it crosses, so its ink takes over from the line with no line across its root, and it still shows in front of whatever lies behind the part.

Face marks are painted on the head's surface, with versions drawn for a few head angles that switch, not blend, as the head turns. Each expression has its own set of angle versions. Each mark's 3D position comes from the body it sits on, never estimated from the drawings. Marks anchor to template coordinates (see [data-model.md](data-model.md), Surface anchor), so they survive refits and oversketches.

## Eyes and gaze

Eyes carry the acting, so they are not loose marks. An eye is a **mark group** with named roles and controls, and the pupils have a movement system of their own.

**Roles.** Lid outline, white, iris, pupil, highlight, brow, lashes. Each role has its own ink and fill rule and a fixed draw order. The lid outline clips the white, iris and pupil. The highlight is the one mark that is camera-locked: it sits where the light and camera put it, not where it was drawn.

Marks are graphic, so an eye's fills take no shadow tone. Near the head's axis a patch's lines of constant v all run to the nose, so an eye's versions are drawn in a frame of the eye region's own, not the patch's u and v, and a brow that reads level from the side does not turn into a frown from ahead.

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
- **Fur has a grain.** Each part has a grain field. Its template gives the default: along the axis toward the end it names, rising off the surface by its lift, and further on a ruff, which rises outward over a stretch of u and v and fades past its edges. A comb tool overrides it, stored as anchor-space vectors on the part, each holding at its place and fading to nothing at its reach. Fur strokes and later hair follow the grain as it stands, so combing moves the strokes already there.
- **Paint and regions cross parts.** A brush stroke or a region border that crosses a socket continues onto the parent patch; a region is a set of anchors on any number of patches. A face mask runs from muzzle to skull and a sock from paw to leg.
- **Paired parts mirror surface work.** Paint, regions and marks on a paired part mirror to its twin by default, with per-side overrides on top, the same rule as outlines.
- **Strokes can leave the surface.** A mark whose anchors carry an offset renders as a 3D line off the surface and moves with its part: whiskers, lashes, drips.
- **Any mark can be camera-locked.** The eye highlight rule applies to a wet nose, a tooth glint and a claw tip.
- **Effect marks belong to the shot.** Sweat drops, shock lines, blush, speed lines and tears are keyed per shot and pinned to a landmark or drawn in camera space. They are not part of the character and do not travel with it to the library.

## World

Both a painted 2D backdrop for the distance and 3D sets and props for what characters touch or walk around. Shadows per scene: none, a flat blob under each character, or flat cast shadows from the scene's light. Only characters are outlined; whether props are inked is open.

## Export note

Outlines and 2D marks are drawn by this tool. A glTF export would carry the body meshes, flat colors and rig, with outlines as an optional inverted-hull mesh. Faces and fur marks need an export plan later. Not needed for the first version.
