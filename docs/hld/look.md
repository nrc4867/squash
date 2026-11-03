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

No textures. Color is painted on the surface by brush or fill, or bounded by a region border drawn as a curve on the surface for crisp edges. Each border can be inked or not.

## Lines

Both modes share the same line rules: thick on the silhouette, thin inside, a line wherever one part overlaps another. The drawn width of an outline means nothing; line rules set rendered weight, and a width tool adjusts it along an outline afterwards. A line-weight adjustment lives on the body surface, so wherever that area is on the silhouette, from any angle, it uses the adjusted weight.

**Where parts meet.** Parts are separate meshes that overlap at their sockets. In flat shading the overlap is invisible, but in two-tone the shadow edge and the silhouette detector both see the crease. So the border at a socket is inked by default, which is also how the reference draws a leg against a body. Switching that ink off is tested in both modes in spike 3.

## Marks

Fur is mainly strokes drawn on the surface, shown where the area nears the silhouette; big shapes such as a tail tip are drawn into the outline. Graftals' rule for keeping tufts evenly spaced on screen may be reused.

Face marks are painted on the head's surface, with versions drawn for a few head angles that switch, not blend, as the head turns. Each expression has its own set of angle versions. Each mark's 3D position comes from the body it sits on, never estimated from the drawings. Marks anchor to template coordinates (see [data-model.md](data-model.md), Surface anchor), so they survive refits and oversketches.

## World

Both a painted 2D backdrop for the distance and 3D sets and props for what characters touch or walk around. Shadows per scene: none, a flat blob under each character, or flat cast shadows from the scene's light. Only characters are outlined; whether props are inked is open.

## Export note

Outlines and 2D marks are drawn by this tool. A glTF export would carry the body meshes, flat colors and rig, with outlines as an optional inverted-hull mesh. Faces and fur marks need an export plan later. Not needed for the first version.
