#!/usr/bin/env bash
# Smoke test: for each node, starts its dedicated server, joins it with its client, then stops both and checks the
# logs. Catches the mod not being loaded at all (wrong metadata), dedicated-server crashes (client classes loaded on
# the server), payload registration problems (the join is refused or disconnected) and exceptions on either side. Windows (Git Bash): the client window is
# closed through PowerShell.
#
#   scripts/smoke-test.sh [--screenshots] 1.21.11-neoforge 1.21.11-fabric ...
#
# --screenshots: the client also screenshots each tab of the music screen with sample data (ScreenshotTour) into
# run/client/screenshots/jukeboxmuse-<node>-*.png, then quits by itself.
# Logs: build/smoke/<node>/{server,client}.log. The server uses run/server (eula.txt accepted, online-mode=false).
set -u
cd "$(dirname "$0")/.."

# wait_for <file> <regex> <seconds>: true once the file has a matching line
wait_for() {
    local i
    for ((i = 0; i < $3; i++)); do
        grep -q -E "$2" "$1" 2>/dev/null && return 0
        sleep 1
    done
    return 1
}

# The java processes of a node's games, found by their command line (one may exit while being looked up). Gradle does not pass a piped "stop" on to the
# server console, so the client window is closed (a normal quit) and the server process is ended.
close_game() {
    powershell.exe -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { \$_.CommandLine -like '*$1*' } | ForEach-Object { \$p = Get-Process -Id \$_.ProcessId -ErrorAction SilentlyContinue; if (\$p) { \$p.CloseMainWindow() | Out-Null } }"
}
kill_game() {
    powershell.exe -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { \$_.CommandLine -like '*$1*' } | ForEach-Object { Stop-Process -Id \$_.ProcessId -Force -ErrorAction SilentlyContinue }"
}

# Lines worth a look: errors and exceptions, minus the dev environment's usual noise
problems() {
    grep -E 'ERROR|Exception|lost connection|Disconnected|Kicked' "$1" \
        | grep -v -E 'Realms|SignedJWT|Anisotropic|server\.properties|NoSuchFileException: server|Failed to load eula|Failed to verify authentication|Narrator|YggdrasilUserApiService|Failed to fetch user properties|Server closed|lost connection: Disconnected|profile key pair|MinecraftClientHttpException: Status: 401|Error parsing option value|sessionserver.mojang.com|SocketTimeoutException|Couldn.t look up profile' \
        | head -20
}

screenshots=""
if [ "${1:-}" = "--screenshots" ]; then
    screenshots="-Pjukeboxmuse.screenshots"
    shift
fi

failed=0
for node in "$@"; do
    out="build/smoke/$node"
    mkdir -p "$out"
    rm -f "$out/server.log" "$out/client.log"
    echo "== $node"

    ./gradlew ":$node:runServer" --console=plain >"$out/server.log" 2>&1 </dev/null &
    server=$!

    if ! wait_for "$out/server.log" 'Done \(|BUILD FAILED' 900 || grep -q 'BUILD FAILED' "$out/server.log"; then
        echo "   server did not start"
        problems "$out/server.log" | sed 's/^/   /'
        failed=1
        kill_game "$node"
        wait $server
        continue
    fi
    echo "   server started"

    ./gradlew ":$node:runClient" -Pjukeboxmuse.join=localhost $screenshots --console=plain >"$out/client.log" 2>&1 &
    client=$!

    if wait_for "$out/server.log" 'joined the game' 600; then
        echo "   client joined"
        if [ -n "$screenshots" ]; then
            wait_for "$out/client.log" 'Screenshot tour finished|BUILD' 180 && echo "   screenshots taken" || echo "   screenshot tour did not finish"
        else
            # Stay a while: the server pushes the music state on join
            sleep 15
        fi
    else
        echo "   client did not join"
        failed=1
    fi
    # The client quits first (its server process has no window, so this only closes the game), then the server
    close_game "$node"
    wait_for "$out/server.log" 'left the game' 60
    wait $client
    kill_game "$node"
    wait $server

    grep -h -E 'joined the game|left the game|lost connection' "$out/server.log" | sed 's/^/   server: /'
    # A mod the loader skipped (e.g. metadata under the wrong name) leaves no error, only a missing line
    if ! grep -q 'Jukebox Muse loaded' "$out/server.log"; then
        echo "   mod not loaded on the server"
        failed=1
    fi
    if ! grep -q 'Jukebox Muse client ready' "$out/client.log"; then
        echo "   mod not loaded on the client"
        failed=1
    fi
    p=$(problems "$out/server.log"; problems "$out/client.log")
    if [ -n "$p" ]; then
        echo "   problems:"
        echo "$p" | sed 's/^/     /'
        failed=1
    fi
done
exit $failed
