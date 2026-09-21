from __future__ import annotations

from tools.material_mapping.compression import MappedPixel
from tools.material_mapping.sampling import inverse_transform

from .pairing import PairedFace


def expand_pairs(pairs: list[PairedFace]) -> list[MappedPixel]:
    result: list[MappedPixel] = []
    for pair in pairs:
        dx, dy, dw, dh = _rect(pair.destination_uv)
        sx, sy, sw, sh = _rect(pair.source_uv)
        for local_y in range(dh):
            for local_x in range(dw):
                source_local_x, source_local_y = inverse_transform(
                    pair.transform, local_x, local_y, sw, sh)
                result.append(MappedPixel(
                    dx + local_x, dy + local_y, pair.slot,
                    sx + source_local_x, sy + source_local_y, pair.transform))
    return result
def _rect(uv: tuple[int, int, int, int]) -> tuple[int, int, int, int]:
    x1, y1, x2, y2 = uv
    return min(x1, x2), min(y1, y2), abs(x2 - x1), abs(y2 - y1)
