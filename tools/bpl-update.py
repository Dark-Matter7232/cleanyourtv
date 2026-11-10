#!/usr/bin/env python3
"""Check and optionally download firmware for the Changhong/BPL OTA client.

This is deliberately a fetch-only tool. It reads public build properties from the TV over ADB,
asks the same HTTPS endpoint used by the TV's ``UpdateLocalSystem`` package, and can save the
returned update file. It never starts the TV updater, writes to the TV, installs a package, changes
recovery commands, or reboots the TV.

Usage:
    TV_HOST=192.168.1.50 tools/venv/bin/python -I tools/bpl-update.py
    TV_HOST=192.168.1.50 tools/venv/bin/python -I tools/bpl-update.py --download firmware/update.zip

The endpoint requires the TV's build fingerprint and project ID, but not its serial number. The
fingerprint is used only in the request and is not written to the downloaded file's metadata.
"""

import argparse
import base64
import hashlib
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path


UPDATE_ENDPOINT = "https://us-central1-changhong-update-test.cloudfunctions.net/otaConfigDownload"
URL_DECRYPTION_KEY = "6578622165786221"
PROJECT_FILES = ("/vendor/project_id/project_id.ini", "/vendor/tvcertificate/project_id.ini")
MAX_RESPONSE_BYTES = 1024 * 1024


def parse_args():
    parser = argparse.ArgumentParser(
        description="Check the BPL/Changhong firmware server and optionally download an update."
    )
    parser.add_argument("--tv-host", default=os.environ.get("TV_HOST"),
                        help="TV IP or hostname (default: TV_HOST)")
    parser.add_argument("--tv-port", default=os.environ.get("TV_PORT", "5555"),
                        help="ADB port (default: TV_PORT or 5555)")
    parser.add_argument("--download", metavar="PATH",
                        help="save and verify the update at PATH; no download occurs without this")
    parser.add_argument("--save-json", metavar="PATH",
                        help="save the server response JSON without TV request details")
    parser.add_argument("--endpoint", default=UPDATE_ENDPOINT, help=argparse.SUPPRESS)
    return parser.parse_args()


def read_props(raw):
    props = {}
    for line in raw.splitlines():
        match = re.match(r"^\[([^\]]+)\]: \[(.*)\]$", line.strip())
        if match:
            props[match.group(1)] = match.group(2)
    return props


def project_id_from(text):
    match = re.search(r"^\s*gProjectID\s*=\s*[\"']?([^\s\"']+)", text, re.MULTILINE)
    if match:
        return match.group(1)
    match = re.search(r"^\s*project_id\s*=\s*[\"']?([^\s\"']+)", text, re.MULTILINE)
    return match.group(1) if match else None


def test_mode_from(text):
    match = re.search(r"\btestmode\s*=\s*(\d+)", text)
    return bool(match and match.group(1) != "0")


def soft_version(props):
    """Mirror the vendor app's V<major>.<minor> conversion, with a build fallback."""
    raw = props.get("vendor.cus.product.fw.version", "").strip()
    if raw:
        parts = raw.split(".")
        if len(parts) >= 2 and len(parts[0]) > 1:
            value = parts[0][1:] + "." + parts[1]
            try:
                return value, int(float(value))
            except ValueError:
                pass

    # Some production BPL builds do not expose vendor.cus.product.fw.version through getprop even
    # though the stock updater still sends a version. RTM5.220609.235 becomes 5.220609, which is
    # the same value the vendor app derives from its usual V5.220609.235 property.
    display = props.get("ro.build.display.id", "")
    match = re.search(r"(?<!\d)(\d+\.\d+)", display)
    if match:
        value = match.group(1)
        return value, int(float(value))
    raise RuntimeError("could not derive the TV software version from its build properties")


def load_tv_info(args):
    if not args.tv_host:
        raise RuntimeError("set TV_HOST or pass --tv-host with the TV's IP address")
    os.environ["TV_HOST"] = args.tv_host
    os.environ["TV_PORT"] = str(args.tv_port)
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    try:
        import tv  # pylint: disable=import-outside-toplevel
    except ImportError as error:
        raise RuntimeError(
            "tools/tv.py needs the packages in tools/requirements.txt; run "
            "python -m venv tools\\venv and tools\\venv\\Scripts\\python.exe -m pip "
            "install -r tools\\requirements.txt"
        ) from error

    try:
        device = tv.connect(timeout=30)
    except Exception as error:  # tv.py has the useful connection-specific wording
        explanation = tv.connect_error(error)
        raise RuntimeError(explanation or str(error)) from error

    try:
        props = read_props(device.shell("getprop"))
        project_text = ""
        for project_file in PROJECT_FILES:
            candidate = device.shell("cat " + project_file + " 2>/dev/null")
            if project_id_from(candidate):
                project_text = candidate
                break
        project_id = project_id_from(project_text)
        if not project_id:
            raise RuntimeError("could not read the TV project ID from /vendor project_id files")

        # This is the same read-only content query used by the vendor application. If the optional
        # test provider is absent, production mode is the safe/default behavior.
        test_text = device.shell(
            "content query --uri content://com.changhong.upgradetest/UpgradeTest "
            "--projection testmode 2>/dev/null"
        )
        version, version_code = soft_version(props)
        return {
            "fingerPrint": props.get("ro.build.fingerprint", ""),
            "projectId": project_id,
            "softVersion": version,
            "softVersionCode": version_code,
            "brand": props.get("ro.product.brand", ""),
            "product": props.get("ro.build.product", props.get("ro.product.device", "")),
            "isTest": test_mode_from(test_text),
            "model": props.get("ro.product.model", ""),
            "build": props.get("ro.build.display.id", ""),
        }
    finally:
        device.close()


class HttpsRedirectHandler(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, file, code, msg, headers, new_url):
        if urllib.parse.urlsplit(new_url).scheme.lower() != "https":
            raise RuntimeError("the firmware server redirected to a non-HTTPS URL; refusing it")
        return super().redirect_request(request, file, code, msg, headers, new_url)


def post_json(endpoint, payload):
    parsed = urllib.parse.urlsplit(endpoint)
    if parsed.scheme.lower() != "https":
        raise RuntimeError("the update endpoint must use HTTPS")
    request = urllib.request.Request(
        endpoint,
        data=json.dumps(payload, separators=(",", ":")).encode("utf-8"),
        headers={
            "Content-Type": "application/json;charset=utf-8",
            "User-Agent": "cleanyourtv-bpl-update/1",
        },
        method="POST",
    )
    opener = urllib.request.build_opener(HttpsRedirectHandler)
    try:
        with opener.open(request, timeout=30) as response:
            data = response.read(MAX_RESPONSE_BYTES + 1)
    except urllib.error.HTTPError as error:
        detail = error.read(2048).decode("utf-8", "replace")
        raise RuntimeError(f"update server returned HTTP {error.code}: {detail}") from error
    if len(data) > MAX_RESPONSE_BYTES:
        raise RuntimeError("update server response is unexpectedly large")
    try:
        return json.loads(data.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise RuntimeError("update server did not return JSON") from error


def decrypt_bin_path(value):
    if not value:
        return ""
    if value.startswith(("https://", "http://")):
        return value
    try:
        from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
        from cryptography.hazmat.primitives.padding import PKCS7

        encrypted = base64.b64decode(value, validate=True)
        decryptor = Cipher(algorithms.AES(URL_DECRYPTION_KEY.encode("ascii")), modes.ECB()).decryptor()
        padded = decryptor.update(encrypted) + decryptor.finalize()
        unpadder = PKCS7(algorithms.AES.block_size).unpadder()
        return (unpadder.update(padded) + unpadder.finalize()).decode("utf-8")
    except Exception as error:
        raise RuntimeError(
            "the server returned an encrypted download URL, but it could not be decoded; "
            "install the repository requirements and try again"
        ) from error


def upgrade_details(response):
    if not isinstance(response, dict):
        raise RuntimeError("update server returned an unexpected JSON object")
    code = response.get("code")
    if code != 1:
        message = response.get("msg") or response.get("message") or "no update available"
        return None, str(message)
    upgrade = response.get("upgrade")
    if not isinstance(upgrade, dict):
        raise RuntimeError("update server reported an update without its metadata")
    details = dict(upgrade)
    details["url"] = decrypt_bin_path(str(details.get("binPath", "")))
    return details, None


def expected_int(value):
    try:
        return int(value)
    except (TypeError, ValueError):
        return 0


def verify_file(path, expected_size, expected_md5):
    md5 = hashlib.md5()
    size = 0
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            size += len(chunk)
            md5.update(chunk)
    if expected_size and size != expected_size:
        raise RuntimeError(f"download size mismatch: got {size} bytes, server says {expected_size}")
    digest = md5.hexdigest()
    if expected_md5 and digest.lower() != str(expected_md5).lower():
        raise RuntimeError(f"download MD5 mismatch: got {digest}, server says {expected_md5}")
    return size, digest


def download(url, destination, expected_size, expected_md5):
    parsed = urllib.parse.urlsplit(url)
    if parsed.scheme.lower() != "https":
        raise RuntimeError("the firmware URL is not HTTPS; refusing to download it")
    destination = Path(destination)
    destination.parent.mkdir(parents=True, exist_ok=True)
    part = destination.with_name(destination.name + ".part")
    request = urllib.request.Request(url, headers={"User-Agent": "cleanyourtv-bpl-update/1"})
    opener = urllib.request.build_opener(HttpsRedirectHandler)
    try:
        with opener.open(request, timeout=60) as response, part.open("wb") as output:
            while True:
                chunk = response.read(1024 * 1024)
                if not chunk:
                    break
                output.write(chunk)
        size, digest = verify_file(part, expected_size, expected_md5)
        os.replace(part, destination)
        return size, digest
    except Exception:
        try:
            part.unlink()
        except FileNotFoundError:
            pass
        raise


def main():
    args = parse_args()
    try:
        info = load_tv_info(args)
        request = {key: info[key] for key in (
            "fingerPrint", "projectId", "softVersion", "softVersionCode", "brand", "product", "isTest"
        )}
        response = post_json(args.endpoint, request)
        if args.save_json:
            Path(args.save_json).write_text(json.dumps(response, indent=2) + "\n", encoding="utf-8")

        details, message = upgrade_details(response)
        print(f"TV: {info['model'] or 'unknown model'}  build: {info['build'] or 'unknown'}")
        if details is None:
            print(message)
            return 0

        print(f"Update: {details.get('version', 'unknown')}")
        if details.get("describe"):
            print(f"Description: {details['describe']}")
        print(f"Size: {expected_int(details.get('size')) or 'unknown'} bytes")
        print(f"MD5: {details.get('md5') or 'not supplied'}")
        if not args.download:
            print("Metadata only; pass --download PATH to fetch it. Nothing was installed.")
            return 0
        if not details.get("url"):
            raise RuntimeError("the update metadata did not contain a download URL")
        size, digest = download(
            details["url"],
            args.download,
            expected_int(details.get("size")),
            details.get("md5"),
        )
        print(f"Downloaded and verified: {args.download} ({size} bytes, MD5 {digest})")
        print("The TV was not changed; install/recovery/reboot are intentionally not performed by this tool.")
        return 0
    except (OSError, RuntimeError, urllib.error.URLError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 2 if "TV" in str(error) or "ADB" in str(error) or "tools/tv.py" in str(error) else 1


if __name__ == "__main__":
    raise SystemExit(main())
