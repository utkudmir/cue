#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from dataclasses import dataclass
from typing import Iterable


@dataclass
class Device:
    name: str
    udid: str
    runtime: str


def run_json(command: list[str]) -> dict:
    completed = subprocess.run(
        command,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        check=False,
    )
    if completed.returncode != 0:
        raise RuntimeError(completed.stderr.strip() or "command_failed")
    return json.loads(completed.stdout)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Resolve or create an iPhone simulator dynamically")
    parser.add_argument("--name", default="", help="Preferred simulator name")
    parser.add_argument("--fallbacks", default="", help="Pipe-separated fallback simulator names")
    parser.add_argument("--label", default="latest-phone", help="Target label/class hint")
    parser.add_argument("--runtime", default="", help="Runtime identifier or name")
    parser.add_argument("--device-type", default="", help="Device type identifier or name")
    parser.add_argument("--device-class", default="", help="Resolved device class (latest-phone/small-phone/large-phone)")
    parser.add_argument("--create-if-missing", action="store_true", default=True)
    parser.add_argument("--no-create-if-missing", action="store_false", dest="create_if_missing")
    return parser.parse_args()


def extract_version_tuple(value: str) -> tuple[int, ...]:
    nums = re.findall(r"\d+", value)
    if not nums:
        return (0,)
    return tuple(int(n) for n in nums)


def infer_device_class(label_hint: str) -> str:
    label = (label_hint or "").strip().lower()
    if "small" in label:
        return "small-phone"
    if "large" in label:
        return "large-phone"
    return "latest-phone"


def class_preferences(device_class: str) -> list[str]:
    if device_class == "small-phone":
        return [
            "iPhone SE (3rd generation)",
            "iPhone 13 mini",
            "iPhone 12 mini",
            "iPhone SE (2nd generation)",
        ]
    if device_class == "large-phone":
        return [
            "iPhone 17 Pro Max",
            "iPhone 16 Pro Max",
            "iPhone 15 Pro Max",
            "iPhone 14 Pro Max",
            "iPhone 16 Plus",
            "iPhone 15 Plus",
            "iPhone 14 Plus",
        ]
    return [
        "iPhone 17 Pro",
        "iPhone 17",
        "iPhone 16 Pro",
        "iPhone 16",
        "iPhone 15 Pro",
        "iPhone 14",
    ]


def unique_non_empty(values: Iterable[str]) -> list[str]:
    out: list[str] = []
    seen: set[str] = set()
    for value in values:
        item = value.strip()
        if not item:
            continue
        if item in seen:
            continue
        seen.add(item)
        out.append(item)
    return out


def is_class_keyword(value: str) -> bool:
    return value in {"latest-phone", "small-phone", "large-phone"}


def load_simctl_state() -> tuple[dict, dict, dict]:
    devices = run_json(["xcrun", "simctl", "list", "devices", "available", "-j"])
    runtimes = run_json(["xcrun", "simctl", "list", "runtimes", "-j"])
    devicetypes = run_json(["xcrun", "simctl", "list", "devicetypes", "-j"])
    return devices, runtimes, devicetypes


def available_ios_runtimes(runtimes_data: dict) -> list[dict]:
    runtimes = []
    for runtime in runtimes_data.get("runtimes", []):
        identifier = runtime.get("identifier", "")
        name = runtime.get("name", "")
        if "iOS" not in identifier and "iOS" not in name:
            continue
        if runtime.get("isAvailable") is False:
            continue
        runtimes.append(runtime)
    runtimes.sort(
        key=lambda item: extract_version_tuple(item.get("version") or item.get("name") or item.get("identifier") or ""),
        reverse=True,
    )
    return runtimes


def resolve_runtime_id(runtime_hint: str, runtimes: list[dict]) -> str:
    if runtime_hint:
        for runtime in runtimes:
            if runtime_hint == runtime.get("identifier"):
                return runtime_hint
        lowered = runtime_hint.lower()
        for runtime in runtimes:
            if lowered == str(runtime.get("name", "")).lower():
                return str(runtime.get("identifier", ""))
    if runtimes:
        return str(runtimes[0].get("identifier", ""))
    return ""


def existing_devices_by_runtime(devices_data: dict) -> dict[str, list[Device]]:
    index: dict[str, list[Device]] = {}
    for runtime_id, entries in devices_data.get("devices", {}).items():
        runtime_devices: list[Device] = []
        for entry in entries:
            if not entry.get("isAvailable", False):
                continue
            name = str(entry.get("name", "")).strip()
            udid = str(entry.get("udid", "")).strip()
            if not name or not udid:
                continue
            runtime_devices.append(Device(name=name, udid=udid, runtime=runtime_id))
        index[runtime_id] = runtime_devices
    return index


def generation_score(name: str) -> int:
    match = re.search(r"iPhone\s+(\d+)", name)
    if not match:
        return 0
    return int(match.group(1))


def class_score(device_class: str, name: str) -> int:
    normalized = name.lower()
    is_small = ("se" in normalized) or ("mini" in normalized)
    is_large = ("pro max" in normalized) or ("plus" in normalized)

    score = generation_score(name)

    if device_class == "small-phone":
        if is_small:
            score += 200
        elif not is_large:
            score += 40
    elif device_class == "large-phone":
        if is_large:
            score += 200
        else:
            score += 20
    else:
        if not is_small:
            score += 100
        if not is_large:
            score += 60
    return score


def find_existing_device(
    candidates: list[str],
    runtime_id: str,
    runtime_order: list[str],
    devices_index: dict[str, list[Device]],
    device_class: str,
) -> Device | None:
    for candidate in candidates:
        preferred_runtimes = [runtime_id] if runtime_id else runtime_order
        for runtime in preferred_runtimes:
            for device in devices_index.get(runtime, []):
                if device.name == candidate:
                    return device
    best_device: Device | None = None
    best_score = -1
    for runtime in runtime_order:
        for device in devices_index.get(runtime, []):
            if not device.name.startswith("iPhone "):
                continue
            score = class_score(device_class, device.name)
            if score > best_score:
                best_score = score
                best_device = device
    if best_device is not None:
        return best_device
    return None


def resolve_device_type_id(
    device_type_hint: str,
    candidates: list[str],
    preferred_names: list[str],
    devicetypes_data: dict,
) -> str:
    devicetypes = devicetypes_data.get("devicetypes", [])
    iPhone_types = [d for d in devicetypes if str(d.get("name", "")).startswith("iPhone ")]

    if device_type_hint:
        if device_type_hint.startswith("com.apple.CoreSimulator.SimDeviceType."):
            if any(device_type_hint == d.get("identifier") for d in iPhone_types):
                return device_type_hint
        lowered = device_type_hint.lower()
        for dtype in iPhone_types:
            if lowered == str(dtype.get("name", "")).lower():
                return str(dtype.get("identifier", ""))

    for name in candidates + preferred_names:
        for dtype in iPhone_types:
            if name == dtype.get("name"):
                return str(dtype.get("identifier", ""))

    if iPhone_types:
        return str(iPhone_types[0].get("identifier", ""))
    return ""


def device_name_for_created_simulator(explicit_name: str, device_class: str) -> str:
    normalized = explicit_name.strip()
    if normalized and not is_class_keyword(normalized.lower()):
        return explicit_name
    return f"DebridHub {device_class}"


def device_type_name(devicetypes_data: dict, identifier: str) -> str:
    for dtype in devicetypes_data.get("devicetypes", []):
        if dtype.get("identifier") == identifier:
            return str(dtype.get("name", ""))
    return ""


def main() -> int:
    args = parse_args()

    try:
        devices_data, runtimes_data, devicetypes_data = load_simctl_state()
    except Exception as exc:  # noqa: BLE001
        print(f"simctl_state_error:{exc}", file=sys.stderr)
        return 1

    device_class = (args.device_class or "").strip().lower()
    if not device_class:
        device_class = infer_device_class(args.label)
    preferred_names = class_preferences(device_class)
    fallback_values = args.fallbacks.split("|") if args.fallbacks else []
    primary_name = args.name.strip()
    if is_class_keyword(primary_name.lower()):
        primary_name = ""
    candidates = unique_non_empty([primary_name, *fallback_values, *preferred_names])

    runtimes = available_ios_runtimes(runtimes_data)
    runtime_order = [str(runtime.get("identifier", "")) for runtime in runtimes if runtime.get("identifier")]
    runtime_id = resolve_runtime_id(args.runtime, runtimes)

    devices_index = existing_devices_by_runtime(devices_data)
    existing = find_existing_device(candidates, runtime_id, runtime_order, devices_index, device_class)
    if existing is not None:
        print(
            "\t".join(
                [
                    existing.name,
                    existing.udid,
                    existing.runtime,
                    "",
                    "false",
                    device_class,
                ]
            )
        )
        return 0

    if not args.create_if_missing:
        print("simulator_missing", file=sys.stderr)
        return 1

    if not runtime_id:
        print("runtime_missing", file=sys.stderr)
        return 1

    device_type_id = resolve_device_type_id(args.device_type, candidates, preferred_names, devicetypes_data)
    if not device_type_id:
        print("iphone_device_type_missing", file=sys.stderr)
        return 1

    simulator_name = device_name_for_created_simulator(args.name.strip(), device_class)
    create = subprocess.run(
        ["xcrun", "simctl", "create", simulator_name, device_type_id, runtime_id],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        check=False,
    )
    if create.returncode != 0:
        print(create.stderr.strip() or "simulator_create_failed", file=sys.stderr)
        return 1

    created_udid = create.stdout.strip()
    if not created_udid:
        print("simulator_create_no_udid", file=sys.stderr)
        return 1

    print(
        "\t".join(
            [
                simulator_name,
                created_udid,
                runtime_id,
                device_type_name(devicetypes_data, device_type_id),
                "true",
                device_class,
            ]
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
