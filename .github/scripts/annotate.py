"""Turn Gradle/Kotlin errors in a build log into GitHub Actions error annotations.

GitHub keeps at most 10 error annotations per step, so errors are packed several to a message.
"""
import os
import re
import sys

log_path, label = sys.argv[1], (sys.argv[2] if len(sys.argv) > 2 else "gradle")
root = os.environ.get("GITHUB_WORKSPACE", os.getcwd()).rstrip("/") + "/"
text = open(log_path, encoding="utf-8", errors="replace").read()
lines = text.splitlines()

errors = []
kotlin = re.compile(r"^e: (?:file://)?(?:\[ksp\] )?(/[^:]+?\.(?:kt|kts|java|xml)):(\d+)(?::(\d+))? (.*)$")
for line in lines:
    m = kotlin.match(line)
    if m:
        path, ln, col, msg = m.groups()
        errors.append(f"{path.replace(root, '')}:{ln}:{col or 1} {msg.strip()}")
    elif line.startswith("e: "):
        errors.append(line[3:].strip())
    elif re.match(r"^\S.*\.(kt|java|xml):\d+: error: ", line):
        errors.append(line.replace(root, "").strip())

# Failing tests and Gradle's own summary.
for i, line in enumerate(lines):
    if re.search(r" FAILED$", line) and ">" in line:
        detail = lines[i + 1].strip() if i + 1 < len(lines) else ""
        errors.append(f"TEST {line.strip()} {detail}")
    if line.strip() == "* What went wrong:":
        block = []
        for nxt in lines[i + 1:i + 12]:
            if nxt.startswith("* Try:"):
                break
            if nxt.strip():
                block.append(nxt.strip())
        errors.append("GRADLE " + " | ".join(block))

seen, unique = set(), []
for e in errors:
    if e not in seen:
        seen.add(e)
        unique.append(e)
if not unique:
    unique = ["No compiler errors found in the log. Last lines: " + " | ".join(lines[-15:])]

def esc(s):
    return s.replace("%", "%25").replace("\r", "").replace("\n", "%0A")


def esc_prop(s):
    return esc(s).replace(":", "%3A").replace(",", "%2C")

chunks = [unique[i:i + 8] for i in range(0, len(unique), 8)][:10]
for n, chunk in enumerate(chunks, 1):
    body = "\n".join(e[:600] for e in chunk)
    title = esc_prop(f"{label} ({n}/{len(chunks)}; {len(unique)} errors)")
    print(f"::error title={title}::{esc(body)}")
