#!/usr/bin/env python3
"""
HFP Hands-Free emulator — pretends to be a BluArmor C30 connecting to our
HfpAgServer over TCP (via `adb forward`).

Runs the full HFP 1.8 SLC setup sequence, then stays connected printing
every AG response. This exercises the AT command handler without needing
Bluetooth pairing or a second phone.

Usage:
    # 1. Start the AG server on the phone (HFP screen → Start Listening)
    # 2. Forward the port:
    adb forward tcp:9222 tcp:9222
    # 3. Run:
    python3 tools/hfp_hf_emulator.py

Press Ctrl-C to disconnect.
"""

import socket
import sys
import time
import threading

HOST = "127.0.0.1"
PORT = 9222

# HF feature bits we advertise (mimic a typical intercom)
#   bit 0: EC/NR
#   bit 2: CLI presentation
#   bit 4: Remote volume control
#   bit 7: Codec negotiation
#   bit 8: HF indicators
HF_FEATURES = (1 << 0) | (1 << 2) | (1 << 4) | (1 << 7) | (1 << 8)  # 405

# Codecs we support: CVSD (1) and mSBC (2)
SUPPORTED_CODECS = "1,2"


def log(direction: str, line: str):
    ts = time.strftime("%H:%M:%S")
    arrow = ">>>" if direction == "tx" else "<<<"
    color = "\033[92m" if direction == "tx" else "\033[96m"
    reset = "\033[0m"
    print(f"{color}{ts} {arrow} {line}{reset}")


def reader_thread(sock: socket.socket):
    """Read and print everything the AG sends, handle codec negotiation."""
    buf = b""
    try:
        while True:
            data = sock.recv(4096)
            if not data:
                print("\n\033[93m--- AG closed the connection ---\033[0m")
                break
            buf += data
            while b"\r\n" in buf:
                line_bytes, buf = buf.split(b"\r\n", 1)
                line = line_bytes.decode("utf-8", errors="replace").strip()
                if not line:
                    continue
                log("rx", line)

                # Respond to codec selection
                if line.startswith("+BCS:"):
                    codec = line.split(":")[1].strip()
                    send_at(sock, f"AT+BCS={codec}")
    except (ConnectionResetError, BrokenPipeError, OSError):
        print("\n\033[93m--- connection lost ---\033[0m")


def send_at(sock: socket.socket, cmd: str):
    log("tx", cmd)
    sock.sendall(f"{cmd}\r\n".encode("utf-8"))


def slc_setup(sock: socket.socket):
    """Run the HFP 1.8 Service Level Connection init sequence."""
    steps = [
        f"AT+BRSF={HF_FEATURES}",
        f"AT+BAC={SUPPORTED_CODECS}",
        "AT+CIND=?",
        "AT+CIND?",
        "AT+CMER=3,0,0,1",
        "AT+CHLD=?",
    ]

    for cmd in steps:
        send_at(sock, cmd)
        time.sleep(0.15)   # give the AG time to respond before the next step

    # Post-SLC optional commands a real HF sends
    extras = [
        "AT+BIND=1,2",
        "AT+BIND?",
        "AT+CLIP=1",
        "AT+CCWA=1",
        "AT+CMEE=1",
        "AT+NREC=0",
        "AT+VGS=12",
        "AT+VGM=8",
        "AT+COPS?",
        "AT+CLCC",
    ]

    for cmd in extras:
        send_at(sock, cmd)
        time.sleep(0.1)


def interactive_mode(sock: socket.socket):
    """Let the user type raw AT commands after SLC setup."""
    print("\n\033[93m--- SLC setup complete. Type AT commands, or press Ctrl-C to quit ---\033[0m\n")
    try:
        while True:
            line = input("\033[90mAT> \033[0m").strip()
            if not line:
                continue
            if not line.upper().startswith("AT") and line not in ("ATA",):
                line = "AT+" + line
            send_at(sock, line)
    except (EOFError, KeyboardInterrupt):
        pass


def main():
    host = HOST
    port = PORT
    if len(sys.argv) > 1:
        parts = sys.argv[1].split(":")
        host = parts[0] if parts[0] else HOST
        port = int(parts[1]) if len(parts) > 1 else int(parts[0])

    print(f"\033[93mHFP HF Emulator — connecting to {host}:{port}\033[0m")
    print(f"\033[93mFeatures: {HF_FEATURES}  Codecs: {SUPPORTED_CODECS}\033[0m\n")

    try:
        sock = socket.create_connection((host, port), timeout=5)
    except (ConnectionRefusedError, OSError) as e:
        print(f"\033[91mCannot connect: {e}\033[0m")
        print("\nChecklist:")
        print("  1. Is the AG server running? (HFP screen → Start Listening)")
        print(f"  2. Did you run: adb forward tcp:{port} tcp:{port}")
        sys.exit(1)

    sock.settimeout(None)

    # Start reading AG responses in the background
    t = threading.Thread(target=reader_thread, args=(sock,), daemon=True)
    t.start()

    time.sleep(0.3)  # let the reader thread settle

    # Run SLC setup
    slc_setup(sock)

    # Wait for all responses, then drop into interactive mode
    time.sleep(1.5)
    interactive_mode(sock)

    print("\n\033[93mDisconnecting.\033[0m")
    sock.close()


if __name__ == "__main__":
    main()
