# High-level design

One file per area, kept current. Code comments may point here with a precise section: `// See HLD: data-model, Surface anchors`.

| File | Area |
| --- | --- |
| [architecture.md](architecture.md) | Decisions, platform, commands, state, derived data, storage, modules, testing |
| [data-model.md](data-model.md) | Entities, the part representation, template and instance, the template file format, schema generation |
| [surface.md](surface.md) | The one surface: the field, the mesher, parts as coordinates, ink, eyes, thin and hard parts, garments and the rig on the surface |
| [parts.md](parts.md) | Every body part and the mechanism that makes it: template, socket, slot or mark, and when |
| [pipeline.md](pipeline.md) | Stages, the method picked for each, the spikes and their pass conditions, patent notes |
| [look.md](look.md) | Art style, shading modes, line rules, marks, where parts meet |
| [sheet-reader.md](sheet-reader.md) | Reading a sheet's views, evidence and the edit page; proposers, the creature proposer and its body plans |

The screens are specified by the boards in [Squash flow of work](https://claude.ai/artifact/KfBGmYcUEuoRJuhvGky3CT). Stretch, the shell these build on, has its own design document and `../stretch/docs/`.
