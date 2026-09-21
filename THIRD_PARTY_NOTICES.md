# Third-party notices

The production Casted Materials JAR does not bundle Create classes, the Create: Biotech mod, or their assets.
It does include material-rendering and mapping code migrated or adapted from the Create: Biotech repository.
Those narrowly scoped Java portions, together with the related authoring tools and tests in the source tree,
retain Nobodiiiii's MIT notice in
[`LICENSES/Nobodiiiii-MIT.txt`](LICENSES/Nobodiiiii-MIT.txt). The notice is packaged in both the production
JAR and Gradle sources JAR as `META-INF/LICENSE_Nobodiiiii.txt`.

Repository test fixtures include unmodified Create and Create: Biotech images plus a user-provided authored
reference. Their precise file groups, ownership, licenses, and the retained `rare_simi` / `rare_jinx` eye-pixel
provenance are recorded next to the fixtures in
[`tools/test-resources/FIXTURE_PROVENANCE.md`](tools/test-resources/FIXTURE_PROVENANCE.md). Those assets remain
under their original terms and are not relicensed by this project's MIT license.

No Cataclysm-derived animation data, CreatePhantom/Allay code, or Sable code is part of this standalone
material-library backport, so their unrelated upstream notices are not included.
