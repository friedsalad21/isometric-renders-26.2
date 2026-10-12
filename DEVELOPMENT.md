# Development notes: Isometric Renders 26.2 port

Unofficial port of [glisco03/isometric-renders](https://github.com/glisco03/isometric-renders) (branch `1.21.4`, MIT)
to **Minecraft 26.2**, Fabric. Runtime deps: Fabric API 0.161.0+26.2, owo-lib 0.13.1+26.2 (owo only exists for 26.2,
which is why the port targets 26.2).

## Status

**Verified** with the client gametest and in-game: item / block (+ block entities) / entity / tooltip / area / item atlas
renders, export to PNG, area selection box, HUD, boots with Sodium.

**Port-only fixes and additions:**
- Exports un-premultiply alpha, so translucent blocks no longer come out washed out.
- Area meshes re-sort translucent quads for the render's view direction, not the player camera.
- Export anti-aliasing: supersampling Off/2x/4x (default 2x), backs off above an 8192 render size.
- Debug overlays in area renders: `render/DebugOverlays` runs the vanilla `DebugRenderer` and Carpet's
  `ShapesRenderer` with the main camera moved to the area's origin, then clips the gizmos to the area.
  Toggles under Mesh Controls. Carpet is optional (`compat/CarpetShapes` is only loaded when it's installed).
  Carpet 26.2 itself doesn't draw Scarpet `label` shapes, so those don't show up in renders either.
- Area render entities: with Freeze Entities on, the mesh keeps copies taken at build time (players are copied as
  `RemotePlayer`s since they can't be created from their type). With it off, entities are looked up from the world
  every frame, so new ones appear and dead ones go away. Show Entities / Show Players hide them, hitboxes included;
  the local player is never drawn.
- `runClientGameTest`: screenshots every render type into `build/run/clientGameTest/screenshots/`.
- Shader packs (Iris): `ISO_SHADERPACKS=<dir with pack zips> ./gradlew -PwithShaders runClientGameTest` adds Iris +
  Sodium to the dev run and `ShaderPackClientGameTest` renders a translucent-heavy scene with every pack on its lowest
  profile (exports in `build/run/clientGameTest/renders/`, one per pack after the plain one). Tested with the 10 most
  downloaded Iris packs: renders match the no-shader one apart from the pack's lighting tweaks (AO strength,
  directional shading). Photon doesn't load on software GL. `compat/IrisCompat` turns off Iris' vertex format
  extension while area meshes build: Iris decides that from a global "extracting a frame" flag, so a background mesh
  build could otherwise get Iris' wider vertex format that vanilla's pipelines can't read.

**Not tested yet:** animation export (needs FFmpeg on PATH), batch renders, copy-to-clipboard, particles in renders,
the `/isorender` commands typed in-game.

**Known differences from upstream:** tooltip renders use the classic purple tooltip look (26.x tooltips are GUI-only
render state, so they're rebuilt from `submitText` + quads; bundle and map tooltip images aren't drawn). Player renders
look players up via the client profile resolver, so they also work in multiplayer.

## Architecture notes (26.2-specific, the non-obvious bits)

- **26.x extracts the GUI before anything renders.** All GPU work (the preview, exports, clipboard) runs inside
  `render/PreviewRenderer` (a `PictureInPictureRenderer`, registered via Fabric's `PictureInPictureRendererRegistry`).
  `RenderScreen` only queues `renderPhaseTasks`. Never draw from `extractRenderState` directly.
- `render/RenderableDispatcher`: `drawIntoTarget` renders a `Renderable` into given color/depth textures via
  `RenderSystem.outputColor/DepthTextureOverride`, a reverse-Z ortho projection and
  `FeatureRenderDispatcher.renderAllFeatures`. `drawIntoImage` returns a `CompletableFuture<NativeImage>`
  (async `copyTextureToBuffer` readback, supersampling and un-premultiply happen there). It's nest-safe:
  batch renders export while the preview is drawing.
- `Renderable.emitVertices(PoseStack, SubmitNodeCollector, delta)` submits geometry; `draw(Matrix4f)` runs after the
  submits are flushed (used by area meshes and particles).
- Lighting: `DefaultRenderable` writes its own Lighting UBO for the custom light angle.
- WorldMesher (gliscowo/worldmesher, MIT) is **vendored** into `src/main/java/io/wispforest/worldmesher` and rewritten
  on vanilla `ModelBlockRenderer`/`FluidRenderer`, with GPU buffers per `ChunkSectionLayer`, drawn with
  `RenderTypes.*MovingBlock().prepare().drawFromBuffer`.
- Render-only entities need `setId(...)` (26.x extraction requires an entity ID).
- 26.x colors are ARGB: an RGB literal like `0xFFFFFF` is invisible. Use `0xFFFFFFFF`.
- Lang file needs `"owo:rich_translations": true` for the JSON-text entries.
- Mixin target strings are plain Mojang names (no refmap). Upstream's were Yarn and all had to be rewritten.

## Rules

1. Check real Minecraft source, never guess APIs: https://mcsrc.dev (version 26.2) or `./gradlew genSources`.
2. Never use or copy code from WikiRenderer (skyblock-wiki/WikiRenderer), even though it's a 26.x fork of this mod.
3. Owo-lib's own 26.2 source is a good reference for 26.2 rendering: `git clone -b 26.2 https://github.com/wisp-forest/owo-lib`
4. After a change: build, run the gametest, **look at the screenshots** (zoom into exports if quality matters).

## Building and testing

```bash
./gradlew build                                                       # jar -> build/libs/
xvfb-run -a -s "-screen 0 1920x1080x24" ./gradlew runClientGameTest   # headless MC client, software GL
ls build/run/clientGameTest/screenshots/ build/run/clientGameTest/renders/
```

The gametest is `src/gametest/java/.../IsometricClientGameTest.java`; extend it for new features rather than guessing.
Without Xvfb, run `./gradlew runClientGameTest` directly. If Minecraft fails to create a window under Xvfb, try
prefixing `LIBGL_ALWAYS_SOFTWARE=1`.

## Releasing

Bump `mod_version` in `gradle.properties`, build, and attach `build/libs/IsometricRenders-<version>.jar` to a new
GitHub release. Older releases stay up so any version can still be downloaded.
