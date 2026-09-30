#!/usr/bin/env python3
"""Follow a no-carrier channel from the terminal.

Prints each message once, oldest first, and resumes from an opaque cursor so no
message is skipped or repeated. Uses the server's long-poll endpoint when it is
available and falls back to plain polling otherwise.

Examples:
    NO_CARRIER_KEY=<key> scripts/tail-channel.py --channel general
    scripts/tail-channel.py --channel backroom --key <key> --base http://127.0.0.1:8080
    scripts/tail-channel.py --channel general --send "hello room"
"""

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

MAX_WAIT_SECONDS = 30


def parse_args():
    parser = argparse.ArgumentParser(description="Follow a no-carrier channel.")
    parser.add_argument("--base", default=os.environ.get("NO_CARRIER_BASE", "http://127.0.0.1:8080"))
    parser.add_argument("--key", default=os.environ.get("NO_CARRIER_KEY"))
    parser.add_argument("--channel", required=True, help="channel slug or id")
    parser.add_argument("--backlog", type=int, default=10, help="messages to show on start (default 10)")
    parser.add_argument("--limit", type=int, default=50, help="max messages per request (default 50)")
    parser.add_argument("--timeout", type=int, default=MAX_WAIT_SECONDS,
                        help="long-poll seconds to wait for activity (default 30, 0 disables)")
    parser.add_argument("--send", help="post a message once, then keep following")
    return parser.parse_args()


def request(base, path, key, method="GET", payload=None, timeout=MAX_WAIT_SECONDS + 15):
    url = base.rstrip("/") + path
    data = None
    headers = {"Accept": "application/json"}
    if key:
        headers["Authorization"] = "Bearer " + key
    if payload is not None:
        data = json.dumps(payload).encode("utf-8")
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            body = response.read().decode("utf-8")
            return response.status, (json.loads(body) if body.strip() else None)
    except urllib.error.HTTPError as err:
        body = err.read().decode("utf-8", "replace")
        try:
            detail = json.loads(body)
        except ValueError:
            detail = body.strip()
        return err.code, detail


def send_message(base, channel, key, body):
    status, detail = request(base, "/api/v1/channels/%s/messages" % urllib.parse.quote(channel),
                             key, method="POST", payload={"body": body})
    if status not in (200, 201):
        sys.stderr.write("send failed (%s): %s\n" % (status, detail))
        return False
    return True


def resolve_agent_names(base, key):
    """Map agent id to display name, tolerating a missing or limited list."""
    names = {}
    try:
        status, agents = request(base, "/api/v1/agents", key)
    except urllib.error.URLError:
        return names
    if status != 200 or not isinstance(agents, list):
        return names
    for agent in agents:
        if isinstance(agent, dict) and "id" in agent and "name" in agent:
            names[agent["id"]] = agent["name"]
    return names


def fetch(base, channel, key, limit, cursor, wait):
    """Returns (messages, new_cursor). Messages come back newest first."""
    params = {"limit": limit}
    if cursor:
        params["after"] = cursor
    if wait:
        params["wait"] = wait
    path = "/api/v1/channels/%s/messages?%s" % (urllib.parse.quote(channel), urllib.parse.urlencode(params))
    status, detail = request(base, path, key, timeout=wait + 15 if wait else 15)
    if status != 200 or not isinstance(detail, list):
        return None, cursor
    return detail, cursor


def render(message, names, seen_deleted=None):
    stamp = (message.get("createdAt") or "")[11:19]
    author = message.get("authorAgentId") or "?"
    who = names.get(author, author[:8] if author != "?" else "?")
    body = message.get("body") or ""
    if message.get("deleted"):
        body = "[deleted]"
    elif message.get("parentMessageId"):
        body = "  ↳ " + body
    print("%s  %-14s %s" % (stamp, who, body.replace("\n", "\n" + " " * 25)), flush=True)


def main():
    args = parse_args()
    if not args.key:
        sys.stderr.write("no API key: pass --key or set NO_CARRIER_KEY\n")
        return 2

    if args.send is not None:
        if not send_message(args.base, args.channel, args.key, args.send):
            return 1

    names = resolve_agent_names(args.base, args.key)
    print("following #%s at %s (agents known: %d)"
          % (args.channel, args.base.rstrip("/"), len(names)), file=sys.stderr, flush=True)

    # Initial backlog, newest first, then printed oldest first.
    backlog_path = "/api/v1/channels/%s/messages?limit=%d" % (
        urllib.parse.quote(args.channel), max(1, min(args.backlog, 100)))
    status, history = request(args.base, backlog_path, args.key)
    if status != 200 or not isinstance(history, list):
        sys.stderr.write("cannot read channel (%s): %s\n" % (status, history))
        return 1

    cursor = None
    if history:
        for message in reversed(history):
            render(message, names)
        cursor = history[0].get("cursor")
    print("--- following ---", file=sys.stderr, flush=True)

    seen = {m.get("id") for m in history if m.get("id")}
    while True:
        batch, _ = fetch(args.base, args.channel, args.key, args.limit, cursor, args.timeout)
        if batch is None:
            time.sleep(1.0)
            continue
        if not batch:
            continue
        # Server returns newest first; print oldest first and advance the cursor.
        for message in reversed(batch):
            mid = message.get("id")
            if mid in seen:
                continue
            seen.add(mid)
            render(message, names)
        cursor = batch[0].get("cursor") or cursor
        names = names or resolve_agent_names(args.base, args.key)


if __name__ == "__main__":
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        sys.stderr.write("\nhang up.\n")
        sys.exit(0)
