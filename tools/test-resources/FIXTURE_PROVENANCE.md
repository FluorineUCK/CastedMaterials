# Test fixture provenance

These files are test inputs only and are excluded from the production JAR.
Copying them into this fixture tree does not change their ownership or license.

- `fixtures/connected_spider/{andesite,brass,copper,railway,shadow_steel,refined_radiance}_casing_connected.png`
  are unmodified Create 1.21.1 / 6.0.10 assets. Create's bundled asset license identifies them as
  **All Rights Reserved**, copyright The Create Team / The Creators of Create.
- `fixtures/connected_spider/{biotech,explosion_proof,explosion_proof_side}_casing*.png`,
  `fixtures/connected_spider/asurine_casing_connected.png`, and `assets/create_biotech/**` are
  byte-for-byte test copies from the Create: Biotech source fixture. Original Create: Biotech assets
  are **All Rights Reserved**, copyright Nobodiiiii, unless the source repository states otherwise.
- `assets/create_biotech/textures/entity/spider_assembly_table/eye_copper_casing.png` contains six
  unchanged pixels from Create's `item/package/rare_simi.png` (x 5 and 10, y 3..5).
  `eye_railway_casing.png` contains four unchanged pixels from Create's
  `item/package/rare_jinx.png` (x 5 and 10, y 5..6). The other package art is not included.
- `fixtures/connected_spider/spider_andesite_authored.png` is the user-provided authored reference
  retained unchanged for pixel-parity testing.

The standalone library's code license is recorded in [`../../LICENSE`](../../LICENSE), with distribution scope
summarized in [`../../THIRD_PARTY_NOTICES.md`](../../THIRD_PARTY_NOTICES.md). Fixture assets listed above are not
relicensed under that code license.
