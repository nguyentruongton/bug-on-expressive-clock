#!/usr/bin/env bash
set -euo pipefail

# Trigger script invoked by systemd user timers for Expressive Clock.
# Usage: alarm-trigger.sh <id> <alarm|timer|alarm-session|timer-session|alarm-notify> [label] [sound_uri]

ALARM_ID="${1:-0}"
KIND="${2:-alarm}"
LABEL="${3:-Expressive Clock}"
SOUND_URI="${4:-}"

SELF_SCRIPT="$(readlink -f "$0" 2>/dev/null || echo "$0")"
RUNTIME_DIR="${XDG_RUNTIME_DIR:-/run/user/$(id -u)}"
CONFIG_DIR="${XDG_CONFIG_HOME:-$HOME/.config}/expressive-clock"
SETTINGS_FILE="$CONFIG_DIR/settings.json"
SOCK_PATH="$RUNTIME_DIR/expressive-clock.sock"
FLAG_FILE="$RUNTIME_DIR/expressive-clock-ringing.flag"
PID_FILE="$RUNTIME_DIR/expressive-clock-audio.pid"
DEV_LAUNCHER="$RUNTIME_DIR/expressive-clock-dev-launch.sh"

resolve_app_bin() {
    if [[ -n "${EXPRESSIVE_CLOCK_BIN:-}" && -x "$EXPRESSIVE_CLOCK_BIN" ]]; then
        echo "$EXPRESSIVE_CLOCK_BIN"
        return
    fi
    local from_path
    from_path="$(command -v expressive-clock 2>/dev/null || true)"
    if [[ -n "$from_path" && -x "$from_path" ]]; then
        echo "$from_path"
        return
    fi
    if [[ -x "/usr/bin/expressive-clock" ]]; then
        echo "/usr/bin/expressive-clock"
        return
    fi
    if [[ -x "$DEV_LAUNCHER" ]]; then
        echo "$DEV_LAUNCHER"
        return
    fi
    echo ""
}

APP_BIN="$(resolve_app_bin)"

read_alarm_setting_int() {
    local key="$1"
    local fallback="$2"
    if [[ -f "$SETTINGS_FILE" ]] && command -v python3 >/dev/null 2>&1; then
        local parsed
        parsed="$(python3 -c 'import json,sys
try:
    d = json.load(open(sys.argv[1]))
    v = int(d.get("alarms", {}).get(sys.argv[2], sys.argv[3]))
    print(max(1, v))
except Exception:
    print(sys.argv[3])' "$SETTINGS_FILE" "$key" "$fallback" 2>/dev/null || echo "$fallback")"
        if [[ "$parsed" =~ ^[0-9]+$ ]] && (( parsed >= 1 )); then
            echo "$parsed"
            return
        fi
    fi
    echo "$fallback"
}

resolve_icon() {
    if [[ -f "/usr/share/icons/hicolor/512x512/apps/expressive-clock.png" ]]; then
        echo "/usr/share/icons/hicolor/512x512/apps/expressive-clock.png"
        return
    fi
    local repo_icon
    repo_icon="$(dirname "$SELF_SCRIPT")/../src/main/resources/icons/expressive-clock-512.png"
    if [[ -f "$repo_icon" ]]; then
        readlink -f "$repo_icon" 2>/dev/null || echo "$repo_icon"
        return
    fi
    echo ""
}

resolve_sound() {
    local uri="${1#file://}"
    if [[ "$uri" == "silent" ]]; then
        echo ""
        return
    fi
    if [[ -n "$uri" && -f "$uri" ]]; then
        echo "$uri"
        return
    fi
    local cache_wav="${XDG_CACHE_HOME:-$HOME/.cache}/expressive-clock/alarm_fallback.wav"
    if [[ -f "$cache_wav" ]]; then
        echo "$cache_wav"
        return
    fi
    local sys_wav="/usr/share/expressive-clock/alarm_fallback.wav"
    if [[ -f "$sys_wav" ]]; then
        echo "$sys_wav"
        return
    fi
    echo ""
}

stop_audio() {
    rm -f "$FLAG_FILE"
    if [[ -f "$PID_FILE" ]]; then
        local pid
        pid="$(cat "$PID_FILE" 2>/dev/null || true)"
        if [[ -n "$pid" ]]; then
            pkill -TERM -P "$pid" 2>/dev/null || true
            kill "$pid" 2>/dev/null || true
        fi
        rm -f "$PID_FILE"
    fi
}

send_socket_ring() {
    if [[ ! -S "$SOCK_PATH" ]]; then
        return 1
    fi
    if command -v socat >/dev/null 2>&1; then
        printf 'RING %s\n' "$ALARM_ID" | socat - "UNIX-CONNECT:$SOCK_PATH" 2>/dev/null
        return $?
    elif command -v python3 >/dev/null 2>&1; then
        python3 -c "import socket,sys; s=socket.socket(socket.AF_UNIX); s.connect(sys.argv[1]); s.sendall(b'RING ' + sys.argv[2].encode() + b'\n'); s.close()" "$SOCK_PATH" "$ALARM_ID" 2>/dev/null
        return $?
    fi
    return 1
}

run_detached_unit() {
    local unit="$1"
    shift
    if command -v systemd-run >/dev/null 2>&1; then
        systemctl --user stop "${unit}.service" >/dev/null 2>&1 || true
        systemd-run --user --collect --unit="$unit" --property=KillMode=control-group "$@" >/dev/null 2>&1
    else
        nohup "$@" >/dev/null 2>&1 &
    fi
}

SNOOZE_MIN="$(read_alarm_setting_int snoozeMinutes 10)"
SILENCE_MIN="$(read_alarm_setting_int silenceAfterMinutes 10)"
SILENCE_MS="$((SILENCE_MIN * 60000))"
ICON_PATH="$(resolve_icon)"
ICON_ARGS=()
if [[ -n "$ICON_PATH" ]]; then
    ICON_ARGS=(--icon="$ICON_PATH")
fi

# ── Detached notification helper (when main app is already open) ───────────────────────────────
if [[ "$KIND" == "alarm-notify" ]]; then
    if command -v notify-send >/dev/null 2>&1; then
        ACTION="$(notify-send --urgency=critical --app-name="Expressive Clock" \
            --category=alarm "${ICON_ARGS[@]}" \
            --expire-time="$SILENCE_MS" \
            --action="snooze=Snooze ${SNOOZE_MIN} min" --action="dismiss=Dismiss" \
            -- "Expressive Clock" "$LABEL" 2>/dev/null || true)"
        if [[ -n "$ACTION" && -n "$APP_BIN" ]]; then
            "$APP_BIN" --action "$ACTION" "$ALARM_ID" >/dev/null 2>&1 || true
        fi
    fi
    exit 0
fi

# ── Trigger entry point (runs inside expressive-clock-alarm-<id>.service) ──────────────────────
if [[ "$KIND" == "alarm" || "$KIND" == "timer" ]]; then
    if [[ "$KIND" == "alarm" ]] && send_socket_ring; then
        # Main app is open and accepted RING; also post a detached notification for quick actions.
        if command -v notify-send >/dev/null 2>&1 && [[ -n "$APP_BIN" ]]; then
            run_detached_unit "expressive-clock-notify-alarm-${ALARM_ID}" \
                "$SELF_SCRIPT" "$ALARM_ID" "alarm-notify" "$LABEL" "$SOUND_URI" || true
        fi
        exit 0
    fi

    # Detach ringing into its own transient service so expressive-clock-alarm-<id>.service exits
    # immediately and never collides with snooze rescheduling or kills children on exit.
    run_detached_unit "expressive-clock-ring-${KIND}-${ALARM_ID}" \
        "$SELF_SCRIPT" "$ALARM_ID" "${KIND}-session" "$LABEL" "$SOUND_URI"
    exit 0
fi

# ── Detached ringing session (runs inside expressive-clock-ring-<kind>-<id>.service) ───────────
cleanup_session() {
    stop_audio
}
trap cleanup_session EXIT INT TERM

# Play looping audio via PipeWire fallback chain (pw-play -> mpv -> aplay).
SOUND_FILE="$(resolve_sound "$SOUND_URI")"
touch "$FLAG_FILE"

if [[ -n "$SOUND_FILE" ]]; then
    if command -v pw-play >/dev/null 2>&1; then
        ( while [[ -f "$FLAG_FILE" ]]; do pw-play "$SOUND_FILE" >/dev/null 2>&1 || break; done ) &
        echo $! > "$PID_FILE"
    elif command -v mpv >/dev/null 2>&1; then
        mpv --no-video --really-quiet --loop=inf "$SOUND_FILE" >/dev/null 2>&1 &
        echo $! > "$PID_FILE"
    elif command -v aplay >/dev/null 2>&1; then
        ( while [[ -f "$FLAG_FILE" ]]; do aplay -q "$SOUND_FILE" >/dev/null 2>&1 || break; done ) &
        echo $! > "$PID_FILE"
    fi
fi

if [[ "$KIND" == "alarm-session" ]]; then
    ACTION=""
    if command -v notify-send >/dev/null 2>&1; then
        ACTION="$(notify-send --urgency=critical --app-name="Expressive Clock" \
            --category=alarm "${ICON_ARGS[@]}" \
            --expire-time="$SILENCE_MS" \
            --action="snooze=Snooze ${SNOOZE_MIN} min" --action="dismiss=Dismiss" \
            -- "Expressive Clock" "$LABEL" 2>/dev/null || true)"
    else
        sleep "$((SILENCE_MIN * 60))" || true
    fi
    stop_audio

    if [[ "$ACTION" == "snooze" ]]; then
        if [[ -n "$APP_BIN" ]]; then
            EXPRESSIVE_CLOCK_IN_RING_SESSION=1 "$APP_BIN" --action snooze "$ALARM_ID" >/dev/null 2>&1 || true
        fi
        if command -v notify-send >/dev/null 2>&1; then
            SNOOZE_UNTIL="$(date -d "+${SNOOZE_MIN} minutes" +%H:%M 2>/dev/null || true)"
            TITLE="Snoozed until ${SNOOZE_UNTIL:-+${SNOOZE_MIN} min}"
            notify-send --urgency=low --app-name="Expressive Clock" \
                "${ICON_ARGS[@]}" \
                --expire-time=5000 \
                -- "$TITLE" "${LABEL} · ${SNOOZE_MIN} min" >/dev/null 2>&1 || true
        fi
    else
        # Dismiss clicked, notification swiped away, or silenceAfterMinutes elapsed.
        if [[ -n "$APP_BIN" ]]; then
            EXPRESSIVE_CLOCK_IN_RING_SESSION=1 "$APP_BIN" --action dismiss "$ALARM_ID" >/dev/null 2>&1 || true
        fi
    fi
    exit 0
fi

# timer-session: native notification with Dismiss only.
if command -v notify-send >/dev/null 2>&1; then
    notify-send --urgency=critical --app-name="Expressive Clock" \
        "${ICON_ARGS[@]}" \
        --expire-time="$SILENCE_MS" \
        --action="dismiss=Dismiss" \
        -- "Expressive Clock" "$LABEL" >/dev/null 2>&1 || true
else
    sleep "$((SILENCE_MIN * 60))" || true
fi
stop_audio
