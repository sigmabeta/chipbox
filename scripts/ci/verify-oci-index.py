#!/usr/bin/env python3
"""Verify a registry-less OCI image archive contains an index for the expected platforms.

`docker buildx build --platform linux/amd64,linux/arm64 --output type=oci,dest=...` writes an
OCI-layout tar: `index.json` points at one or more manifests, and a multi-platform build usually
nests a second index blob whose descriptors carry the platform. This script follows those nested
indexes and asserts that every requested `os/arch` is present, so a release job can prove both
architectures were actually produced without a registry (`docker buildx imagetools inspect` wants
one, and `--load` only accepts a single platform).

Usage:
  scripts/ci/verify-oci-index.py <image.oci.tar> [os/arch ...]
  scripts/ci/verify-oci-index.py --self-test

Exit codes: 0 = all expected platforms present, 1 = missing/unreadable, 2 = usage.
"""

import io
import json
import os
import sys
import tarfile

INDEX_MEDIA_TYPES = (
    "application/vnd.oci.image.index.v1+json",
    "application/vnd.docker.distribution.manifest.list.v2+json",
)


def _load_json(tf, name):
    entry = tf.extractfile(name)
    if entry is None:
        raise ValueError("missing %s" % name)
    return json.load(entry)


def _platforms(tf, doc, seen):
    """Yield `os/arch` strings from an index document, following nested indexes."""
    for manifest in doc.get("manifests") or []:
        platform = manifest.get("platform") or {}
        os_name = platform.get("os")
        arch = platform.get("architecture")
        if os_name and arch:
            yield "%s/%s" % (os_name, arch)
            continue

        if manifest.get("mediaType") not in INDEX_MEDIA_TYPES:
            continue

        digest = manifest.get("digest") or ""
        if ":" not in digest:
            continue
        # sha256:<hex> -> blobs/sha256/<hex>
        blob = "blobs/" + digest.replace(":", "/", 1)
        if blob in seen:
            continue
        seen.add(blob)
        try:
            nested = _load_json(tf, blob)
        except (KeyError, ValueError):
            continue
        for found in _platforms(tf, nested, seen):
            yield found


def archive_platforms(path):
    with tarfile.open(path) as tf:
        names = set(tf.getnames())
        if "index.json" not in names:
            raise ValueError("%s has no index.json (not an OCI archive?)" % path)
        seen = set()
        return sorted(set(_platforms(tf, _load_json(tf, "index.json"), seen)))


def verify(path, expected):
    found = archive_platforms(path)
    missing = [p for p in expected if p not in found]
    if missing:
        sys.stderr.write(
            "FAILED: %s is missing platform(s): %s (found: %s)\n"
            % (path, ", ".join(missing), ", ".join(found) or "none")
        )
        return 1
    print("OCI index OK: %s contains %s" % (path, ", ".join(found)))
    return 0


def _add_bytes(tf, name, data):
    info = tarfile.TarInfo(name)
    info.size = len(data)
    tf.addfile(info, io.BytesIO(data))


def self_test():
    """Build a nested-index archive and prove the parser follows it (and rejects a missing arch)."""
    import hashlib
    import tempfile

    inner = {
        "schemaVersion": 2,
        "mediaType": INDEX_MEDIA_TYPES[0],
        "manifests": [
            {"platform": {"os": "linux", "architecture": "amd64"}},
            {"platform": {"os": "linux", "architecture": "arm64"}},
        ],
    }
    raw = json.dumps(inner).encode("utf-8")
    digest = "sha256:" + hashlib.sha256(raw).hexdigest()
    outer = {
        "schemaVersion": 2,
        "mediaType": INDEX_MEDIA_TYPES[0],
        "manifests": [{"mediaType": INDEX_MEDIA_TYPES[0], "digest": digest}],
    }

    with tempfile.TemporaryDirectory() as tmp:
        path = os.path.join(tmp, "image.oci.tar")
        with tarfile.open(path, "w") as tf:
            _add_bytes(tf, "oci-layout", b'{"imageLayoutVersion":"1.0.0"}')
            _add_bytes(tf, "index.json", json.dumps(outer).encode("utf-8"))
            _add_bytes(tf, "blobs/" + digest.replace(":", "/", 1), raw)

        if verify(path, ["linux/amd64", "linux/arm64"]) != 0:
            return 1
        # The negative case intentionally fails; keep its diagnostic out of the caller's output.
        import contextlib

        with contextlib.redirect_stderr(io.StringIO()):
            rejected = verify(path, ["linux/amd64", "linux/s390x"])
        if rejected == 0:
            sys.stderr.write("FAILED: self-test accepted a platform that is not in the archive\n")
            return 1
    print("verify-oci-index.py self-test OK")
    return 0


def main(argv):
    if len(argv) < 2 or argv[1] in ("-h", "--help"):
        sys.stderr.write(__doc__)
        return 2
    if argv[1] == "--self-test":
        return self_test()
    if len(argv) < 3:
        sys.stderr.write("usage: %s <image.oci.tar> <os/arch> [...]\n" % argv[0])
        return 2
    try:
        return verify(argv[1], argv[2:])
    except (OSError, tarfile.TarError, ValueError) as exc:
        sys.stderr.write("FAILED: %s\n" % exc)
        return 1


if __name__ == "__main__":
    sys.exit(main(sys.argv))
