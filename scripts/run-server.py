#!/usr/bin/env python3
"""Launch the executable server jar with local env values, without shell evaluation."""

import argparse
import os
from pathlib import Path
import re
import shlex
import shutil
import sys


def read_env(path: Path) -> dict[str, str]:
    values = {}
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("export "):
            line = line[7:]
        key, separator, value = line.partition("=")
        key = key.strip()
        if not separator or not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", key):
            raise ValueError(f"Invalid env assignment at line {number}")
        try:
            values[key] = " ".join(shlex.split(value, comments=True))
        except ValueError:
            raise ValueError(f"Invalid env quoting at line {number}") from None
    return values


def main() -> None:
    root = Path(__file__).resolve().parents[1]
    server = root / "agentstart-server"
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env", type=Path, default=server / ".env.local")
    parser.add_argument("--jar", type=Path, help="Explicit executable server jar")
    parser.add_argument("--port", type=int, help="Override the local HTTP port")
    args = parser.parse_args()
    environment = os.environ.copy()
    environment.update(read_env(args.env.resolve()))
    if args.port is not None:
        if not 1 <= args.port <= 65535:
            raise ValueError("Port must be between 1 and 65535")
        environment["SERVER_PORT"] = str(args.port)
    candidates = [args.jar.resolve()] if args.jar else list((server / "target").glob("agentstart-server-*.jar"))
    if len(candidates) != 1 or not candidates[0].is_file():
        raise ValueError("Build the server first: mvn -pl agentstart-server -am package -DskipTests")
    java_home = environment.get("JAVA_HOME")
    java = str(Path(java_home) / "bin" / "java") if java_home else shutil.which("java")
    if not java or not Path(java).is_file():
        raise ValueError("Java 21 is required; configure JAVA_HOME or PATH")
    os.chdir(server)
    os.execve(java, [java, "-jar", str(candidates[0])], environment)


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError) as error:
        print(f"Server launch failed: {error}", file=sys.stderr)
        sys.exit(1)
