from __future__ import annotations

from typing import Mapping

from PIL import Image

from .compression import MappedPixel
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


def render_pixels(
        pixels: list[MappedPixel], size: tuple[int, int],
        sources: Mapping[str, Image.Image], grids: Mapping[str, tuple[int, int]]) -> Image.Image:
    output = Image.new("RGBA", size, (0, 0, 0, 0))
    written: dict[tuple[int, int], tuple[int, int, int, int]] = {}
    for pixel in pixels:
        source = sources[pixel.slot]
        grid = grids[pixel.slot]
        scale = _integer_scale(source.size, grid, pixel.slot)
        color = source.getpixel((pixel.source_x * scale, pixel.source_y * scale))
        destination = pixel.destination_x, pixel.destination_y
        previous = written.get(destination)
        if previous is not None and previous != color:
            raise ValueError(f"conflicting preview colors at {destination[0]},{destination[1]}")
        written[destination] = color
        output.putpixel(destination, color)
    return output


def render_definition(
        definition: dict, sources: Mapping[str, Image.Image]) -> Image.Image:
    grids = {slot: tuple(grid) for slot, grid in definition["source_grids"].items()}
    mapped: list[MappedPixel] = []
    for region in definition.get("regions", []):
        sx, sy, sw, sh = region["source"]
        dx, dy, dw, dh = region["destination"]
        transform = region.get("transform", "identity")
        for local_y in range(dh):
            for local_x in range(dw):
                source_local_x, source_local_y = inverse_transform(
                    transform, local_x, local_y, sw, sh)
                mapped.append(MappedPixel(
                    dx + local_x, dy + local_y, region["source_slot"],
                    sx + source_local_x, sy + source_local_y, transform))
    for pixel in definition.get("pixels", []):
        mapped.append(MappedPixel(
            pixel["destination"][0], pixel["destination"][1], pixel["source_slot"],
            pixel["source"][0], pixel["source"][1], "identity"))
    return render_pixels(mapped, tuple(definition["size"]), sources, grids)


def inverse_transform(
        transform: str, x: int, y: int, source_width: int, source_height: int) -> tuple[int, int]:
    if transform == "identity":
        return x, y
    if transform == "rotate_90":
        return y, source_height - 1 - x
    if transform == "rotate_180":
        return source_width - 1 - x, source_height - 1 - y
    if transform == "rotate_270":
        return source_width - 1 - y, x
    if transform == "mirror_x":
        return source_width - 1 - x, y
    if transform == "mirror_y":
        return x, source_height - 1 - y
    raise ValueError(f"unknown mapping transform {transform}")


def _rect(uv: tuple[int, int, int, int]) -> tuple[int, int, int, int]:
    x1, y1, x2, y2 = uv
    return min(x1, x2), min(y1, y2), abs(x2 - x1), abs(y2 - y1)


def _integer_scale(actual: tuple[int, int], grid: tuple[int, int], slot: str) -> int:
    if grid[0] <= 0 or grid[1] <= 0 or actual[0] % grid[0] or actual[1] % grid[1]:
        raise ValueError(f"source {slot} size {actual[0]}x{actual[1]} is not an integer grid scale")
    scale_x, scale_y = actual[0] // grid[0], actual[1] // grid[1]
    if scale_x != scale_y or scale_x <= 0:
        raise ValueError(f"source {slot} has a non-uniform grid scale")
    return scale_x
