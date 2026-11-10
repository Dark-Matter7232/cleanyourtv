# BPL/Changhong firmware fetcher

`bpl-update.py` checks the HTTPS firmware service used by the BPL/Changhong
`com.homwee.dmt.upgrade` package. It reads the TV's build properties and project ID over ADB, asks
the server for a matching update, and optionally downloads the returned file.

It is fetch-only: it does not launch the stock updater, write anything to the TV, install the ZIP,
write recovery commands, reboot, or close ADB. The server request contains the build fingerprint,
project ID, software version, brand, and product. It does not send the TV serial number.

The normal check is:

```bash
TV_HOST=192.168.1.50 tools/venv/bin/python -I tools/bpl-update.py
```

To save an update after the check reports one:

```bash
TV_HOST=192.168.1.50 tools/venv/bin/python -I tools/bpl-update.py --download firmware/bpl-update.zip
```

On Windows:

```powershell
$env:TV_HOST = "192.168.1.50"
tools\venv\Scripts\python.exe -I tools\bpl-update.py --download firmware\bpl-update.zip
```

The download is written to a `.part` file first and is moved into place only after the server's
size and MD5 checks pass. The script never installs the downloaded firmware; use the TV's own update
UI if you decide to apply it, after checking the release and keeping a recovery path.
