from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True, order=True)
class MappedPixel:
    destination_x: int
    destination_y: int
    slot: str
    source_x: int
    source_y: int
    transform: str = "identity"


@dataclass(frozen=True)
class CompressionResult:
    regions: list[dict]
    pixels: list[dict]


@dataclass
class _Run:
    x: int
    y: int
    width: int
    height: int
    slot: str
    transform: str
    source_x: int
    source_y: int
    members: list[MappedPixel]


HORIZONTAL_DELTA = {
    "identity": (1, 0),
    "rotate_90": (0, -1),
    "rotate_180": (-1, 0),
    "rotate_270": (0, 1),
    "mirror_x": (-1, 0),
    "mirror_y": (1, 0),
}
VERTICAL_DELTA = {
    "identity": (0, 1),
    "rotate_90": (1, 0),
    "rotate_180": (0, -1),
    "rotate_270": (-1, 0),
    "mirror_x": (0, 1),
    "mirror_y": (0, -1),
}


def compress_pixels(pixels: list[MappedPixel]) -> CompressionResult:
    unique = _unique_pixels(pixels)
    horizontal = _horizontal_runs(sorted(unique.values(), key=lambda pixel: (
        pixel.destination_y, pixel.destination_x, pixel.slot,
        pixel.transform, pixel.source_y, pixel.source_x)))
    merged = _merge_vertical(horizontal)
    regions: list[dict] = []
    sparse: list[dict] = []
    for run in sorted(merged, key=lambda item: (
            item.y, item.x, item.slot, item.source_y, item.source_x, item.transform)):
        if run.width * run.height == 1:
            pixel = run.members[0]
            sparse.append({
                "source_slot": pixel.slot,
                "source": [pixel.source_x, pixel.source_y],
                "destination": [pixel.destination_x, pixel.destination_y],
            })
            continue
        source_x = min(pixel.source_x for pixel in run.members)
        source_y = min(pixel.source_y for pixel in run.members)
        source_width = max(pixel.source_x for pixel in run.members) - source_x + 1
        source_height = max(pixel.source_y for pixel in run.members) - source_y + 1
        regions.append({
            "source_slot": run.slot,
            "source": [source_x, source_y, source_width, source_height],
            "destination": [run.x, run.y, run.width, run.height],
            "transform": run.transform,
        })
    return CompressionResult(regions, sparse)


def _unique_pixels(pixels: list[MappedPixel]) -> dict[tuple[int, int], MappedPixel]:
    result: dict[tuple[int, int], MappedPixel] = {}
    for pixel in pixels:
        key = pixel.destination_x, pixel.destination_y
        previous = result.get(key)
        if previous is not None and previous != pixel:
            raise ValueError(
                f"conflicting writes at destination {pixel.destination_x},{pixel.destination_y}")
        result[key] = pixel
    return result


def _horizontal_runs(pixels: list[MappedPixel]) -> list[_Run]:
    result: list[_Run] = []
    current: _Run | None = None
    previous: MappedPixel | None = None
    for pixel in pixels:
        delta = HORIZONTAL_DELTA.get(pixel.transform)
        if delta is None:
            raise ValueError(f"unknown mapping transform {pixel.transform}")
        extends = (
            current is not None and previous is not None
            and pixel.destination_y == previous.destination_y
            and pixel.destination_x == previous.destination_x + 1
            and pixel.slot == previous.slot
            and pixel.transform == previous.transform
            and pixel.source_x == previous.source_x + delta[0]
            and pixel.source_y == previous.source_y + delta[1]
        )
        if not extends:
            current = _Run(
                pixel.destination_x, pixel.destination_y, 0, 1,
                pixel.slot, pixel.transform, pixel.source_x, pixel.source_y, [])
            result.append(current)
        current.width += 1
        current.members.append(pixel)
        previous = pixel
    return result


def _merge_vertical(runs: list[_Run]) -> list[_Run]:
    result: list[_Run] = []
    active: dict[tuple[int, int, str, str], _Run] = {}
    for run in sorted(runs, key=lambda item: (item.y, item.x, item.slot, item.transform)):
        key = run.x, run.width, run.slot, run.transform
        previous = active.get(key)
        delta = VERTICAL_DELTA[run.transform]
        extends = (
            previous is not None
            and run.y == previous.y + previous.height
            and run.source_x == previous.source_x + delta[0] * previous.height
            and run.source_y == previous.source_y + delta[1] * previous.height
        )
        if extends:
            previous.height += 1
            previous.members.extend(run.members)
        else:
            result.append(run)
            active[key] = run
    return result
