#!/usr/bin/env python3
"""
Mac intercom client — connects to the RiderIntercom app on the phone,
captures audio from the C30 mic, and plays received voice through the Mac.

Setup:
  1. Connect C30 to the Mac via Bluetooth (it becomes the default mic)
  2. On the phone, start hosting (the app listens on RFCOMM)
  3. We bypass RFCOMM and use TCP:
       - The phone app needs the TCP test listener on port 9333
       - Forward: adb forward tcp:9333 tcp:9333
  4. Run: python3 tools/mac_intercom.py

Protocol: matches RiderIntercom's wire format exactly.
  Header: SYNC(0xAA) type(1) seq(2) flags(1) len(2) payload(len)
  HELLO:  version(2) sampleRate(4) frameSamples(2) codec(1) sessionId(8) nameLen(1) name(14)
  AUDIO:  163 bytes of IMA ADPCM (3-byte state + 160 bytes = 320 samples @ 16kHz = 20ms)
"""

import socket
import struct
import threading
import time
import sys
import signal
import random
import numpy as np

try:
    import sounddevice as sd
except ImportError:
    print("pip install sounddevice numpy")
    sys.exit(1)

# --------------- Protocol constants ---------------

SYNC = 0xAA
HEADER_SIZE = 7
MAX_PAYLOAD = 512

TYPE_AUDIO = 0x01
TYPE_PING = 0x02
TYPE_FLOOR_TAKEN = 0x03
TYPE_FLOOR_RELEASE = 0x04
TYPE_FLOOR_DENIED = 0x05
TYPE_END = 0x06
TYPE_PONG = 0x07
TYPE_SWITCH = 0x08
TYPE_HELLO = 0x10

HELLO_SIZE = 32
PROTOCOL_VERSION = 1
SAMPLE_RATE = 16000
FRAME_SAMPLES = 320  # 20 ms
FRAME_MS = 20
CODEC_ADPCM = 1

# --------------- IMA ADPCM ---------------

STEP_TABLE = [
    7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 21, 23, 25, 28, 31, 34, 37, 41, 45,
    50, 55, 60, 66, 73, 80, 88, 97, 107, 118, 130, 143, 157, 173, 190, 209, 230,
    253, 279, 307, 337, 371, 408, 449, 494, 544, 598, 658, 724, 796, 876, 963,
    1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066, 2272, 2499, 2749, 3024, 3327,
    3660, 4026, 4428, 4871, 5358, 5894, 6484, 7132, 7845, 8630, 9493, 10442,
    11487, 12635, 13899, 15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794,
    32767,
]
INDEX_TABLE = [-1, -1, -1, -1, 2, 4, 6, 8, -1, -1, -1, -1, 2, 4, 6, 8]


class AdpcmEncoder:
    def __init__(self):
        self.predictor = 0
        self.index = 0

    def encode_frame(self, pcm: np.ndarray) -> bytes:
        out = bytearray(3 + len(pcm) // 2)
        # State header
        out[0] = (self.predictor >> 8) & 0xFF
        out[1] = self.predictor & 0xFF
        out[2] = self.index & 0xFF

        o = 3
        i = 0
        while i < len(pcm):
            lo = self._encode_sample(int(pcm[i]))
            hi = self._encode_sample(int(pcm[i + 1]))
            out[o] = (hi << 4) | lo
            o += 1
            i += 2
        return bytes(out)

    def _encode_sample(self, sample: int) -> int:
        step = STEP_TABLE[self.index]
        diff = sample - self.predictor
        code = 0
        if diff < 0:
            code = 8
            diff = -diff

        vpdiff = step >> 3
        s = step
        if diff >= s:
            code |= 4; diff -= s; vpdiff += s
        s >>= 1
        if diff >= s:
            code |= 2; diff -= s; vpdiff += s
        s >>= 1
        if diff >= s:
            code |= 1; vpdiff += s

        if code & 8:
            self.predictor -= vpdiff
        else:
            self.predictor += vpdiff
        self.predictor = max(-32768, min(32767, self.predictor))
        self.index = max(0, min(len(STEP_TABLE) - 1, self.index + INDEX_TABLE[code]))
        return code


def adpcm_decode(data: bytes) -> np.ndarray:
    if len(data) <= 3:
        return np.zeros(0, dtype=np.int16)
    predictor = struct.unpack(">h", data[0:2])[0]
    index = data[2] & 0xFF
    index = max(0, min(len(STEP_TABLE) - 1, index))

    samples = (len(data) - 3) * 2
    out = np.zeros(samples, dtype=np.int16)
    o = 0
    for i in range(3, len(data)):
        byte = data[i]
        for code in [byte & 0x0F, (byte >> 4) & 0x0F]:
            if o >= samples:
                break
            step = STEP_TABLE[index]
            vpdiff = step >> 3
            if code & 4: vpdiff += step
            if code & 2: vpdiff += step >> 1
            if code & 1: vpdiff += step >> 2
            if code & 8:
                predictor -= vpdiff
            else:
                predictor += vpdiff
            predictor = max(-32768, min(32767, predictor))
            index = max(0, min(len(STEP_TABLE) - 1, index + INDEX_TABLE[code]))
            out[o] = predictor
            o += 1
    return out


# --------------- Wire protocol ---------------

def encode_message(msg_type: int, seq: int, flags: int, payload: bytes) -> bytes:
    header = struct.pack(">BBHBH", SYNC, msg_type, seq & 0xFFFF, flags, len(payload))
    return header + payload


def make_hello(rider_name: str) -> bytes:
    session_id = random.randint(0, (1 << 63) - 1)
    name_bytes = rider_name.encode("utf-8")[:14]
    buf = struct.pack(">HIHBq", PROTOCOL_VERSION, SAMPLE_RATE, FRAME_SAMPLES,
                      CODEC_ADPCM, session_id)
    buf += struct.pack("B", len(name_bytes))
    buf += name_bytes
    buf += b"\x00" * (HELLO_SIZE - len(buf))
    return buf


def parse_hello(data: bytes):
    if len(data) < HELLO_SIZE:
        return None
    version, sr, fs, codec, sid = struct.unpack(">HIHBq", data[:17])
    nl = data[17]
    name = data[18:18 + nl].decode("utf-8", errors="replace")
    return {"version": version, "sampleRate": sr, "frameSamples": fs,
            "codec": codec, "sessionId": sid, "name": name}


def read_message(sock: socket.socket):
    """Read one framed message from the socket."""
    # Find SYNC
    while True:
        b = sock.recv(1)
        if not b:
            return None
        if b[0] == SYNC:
            break

    # Read rest of header
    hdr = _recv_exact(sock, HEADER_SIZE - 1)
    if hdr is None:
        return None
    msg_type = hdr[0]
    seq = (hdr[1] << 8) | hdr[2]
    flags = hdr[3]
    length = (hdr[4] << 8) | hdr[5]

    if length > MAX_PAYLOAD:
        return None

    payload = _recv_exact(sock, length) if length > 0 else b""
    if payload is None:
        return None

    return {"type": msg_type, "seq": seq, "flags": flags, "payload": payload}


def _recv_exact(sock, n):
    buf = b""
    while len(buf) < n:
        chunk = sock.recv(n - len(buf))
        if not chunk:
            return None
        buf += chunk
    return buf


# --------------- Audio ---------------

class AudioBridge:
    def __init__(self, input_device=None, output_device=None):
        self.encoder = AdpcmEncoder()
        self.sock = None
        self.seq = 0
        self.running = False
        self.input_device = input_device
        self.output_device = output_device
        self.play_buffer = []
        self.play_lock = threading.Lock()

    def next_seq(self):
        self.seq = (self.seq + 1) & 0xFFFF
        return self.seq

    def start_capture(self, sock: socket.socket):
        self.sock = sock
        self.running = True

        def mic_callback(indata, frames, time_info, status):
            if not self.running:
                return
            pcm = (indata[:, 0] * 32767).astype(np.int16)
            if len(pcm) != FRAME_SAMPLES:
                return
            adpcm = self.encoder.encode_frame(pcm)
            msg = encode_message(TYPE_AUDIO, self.next_seq(), 0, adpcm)
            try:
                sock.sendall(msg)
            except Exception:
                self.running = False

        self.input_stream = sd.InputStream(
            device=self.input_device,
            samplerate=SAMPLE_RATE,
            blocksize=FRAME_SAMPLES,
            channels=1,
            dtype="float32",
            callback=mic_callback,
        )
        self.input_stream.start()

        # Output stream for playback
        self.output_stream = sd.OutputStream(
            device=self.output_device,
            samplerate=SAMPLE_RATE,
            blocksize=FRAME_SAMPLES,
            channels=1,
            dtype="float32",
            callback=self._play_callback,
        )
        self.output_stream.start()

    def _play_callback(self, outdata, frames, time_info, status):
        with self.play_lock:
            if self.play_buffer:
                pcm = self.play_buffer.pop(0)
                outdata[:len(pcm), 0] = pcm / 32767.0
                if len(pcm) < frames:
                    outdata[len(pcm):, 0] = 0
            else:
                outdata[:, 0] = 0

    def enqueue_audio(self, payload: bytes):
        pcm = adpcm_decode(payload)
        with self.play_lock:
            if len(self.play_buffer) < 10:
                self.play_buffer.append(pcm)

    def stop(self):
        self.running = False
        if hasattr(self, "input_stream"):
            self.input_stream.stop()
        if hasattr(self, "output_stream"):
            self.output_stream.stop()


# --------------- Main ---------------

TYPE_NAMES = {
    TYPE_AUDIO: "AUDIO", TYPE_PING: "PING", TYPE_PONG: "PONG",
    TYPE_FLOOR_TAKEN: "FLOOR_TAKEN", TYPE_FLOOR_RELEASE: "FLOOR_RELEASE",
    TYPE_FLOOR_DENIED: "FLOOR_DENIED", TYPE_END: "END",
    TYPE_SWITCH: "SWITCH", TYPE_HELLO: "HELLO",
}


def log(msg):
    ts = time.strftime("%H:%M:%S")
    print(f"\033[93m{ts}\033[0m {msg}")


def main():
    host = "127.0.0.1"
    port = 9333

    if len(sys.argv) > 1:
        addr = sys.argv[1]
        if ":" in addr:
            host, port = addr.rsplit(":", 1)
            port = int(port)
        else:
            host = addr

    # Find C30 as input device
    devs = sd.query_devices()
    c30_idx = None
    for i, d in enumerate(devs):
        if "C30" in d["name"] and d["max_input_channels"] > 0:
            c30_idx = i
            break

    print("\033[93m=== Mac Intercom Client ===\033[0m\n")

    if c30_idx is not None:
        print(f"Input:  [{c30_idx}] {devs[c30_idx]['name']} (C30 mic)")
    else:
        c30_idx = sd.default.device[0]
        print(f"Input:  [{c30_idx}] {devs[c30_idx]['name']} (default — C30 not found)")

    out_idx = sd.default.device[1]
    print(f"Output: [{out_idx}] {devs[out_idx]['name']}")
    print(f"Connecting to {host}:{port}...\n")

    try:
        sock = socket.create_connection((host, port), timeout=5)
    except Exception as e:
        print(f"\033[91mCan't connect: {e}\033[0m")
        print("\nChecklist:")
        print(f"  1. Is the phone hosting? (Start hosting in the app)")
        print(f"  2. TCP listener on phone port {port}")
        print(f"  3. adb forward tcp:{port} tcp:{port}")
        sys.exit(1)

    sock.settimeout(None)
    log("Connected!")

    # Send HELLO
    hello = make_hello("Mac")
    msg = encode_message(TYPE_HELLO, 0, 0, hello)
    sock.sendall(msg)
    log("Sent HELLO")

    # Wait for peer HELLO
    peer_hello = None
    while True:
        m = read_message(sock)
        if m is None:
            log("Connection closed before HELLO")
            sys.exit(1)
        if m["type"] == TYPE_HELLO:
            peer_hello = parse_hello(m["payload"])
            log(f"Peer HELLO: {peer_hello}")
            break
        else:
            log(f"Pre-HELLO message: {TYPE_NAMES.get(m['type'], hex(m['type']))}")

    # Start audio bridge
    bridge = AudioBridge(input_device=c30_idx, output_device=out_idx)
    bridge.start_capture(sock)
    log("Audio bridge running — speak into C30!")

    # Send FLOOR_TAKEN to indicate we're talking
    sock.sendall(encode_message(TYPE_FLOOR_TAKEN, bridge.next_seq(), 0, b""))

    audio_count = 0
    last_status = time.time()

    def handle_signal(sig, frame):
        log("Shutting down...")
        bridge.stop()
        try:
            sock.sendall(encode_message(TYPE_END, bridge.next_seq(), 0, b""))
        except Exception:
            pass
        sock.close()
        sys.exit(0)

    signal.signal(signal.SIGINT, handle_signal)

    # Read loop
    while bridge.running:
        m = read_message(sock)
        if m is None:
            log("Connection closed")
            break

        t = m["type"]
        if t == TYPE_AUDIO:
            bridge.enqueue_audio(m["payload"])
            audio_count += 1
            now = time.time()
            if now - last_status > 5:
                log(f"  audio frames rx: {audio_count}")
                audio_count = 0
                last_status = now
        elif t == TYPE_PING:
            # Reply with PONG
            origin = m["payload"]
            now_us = int(time.time() * 1_000_000)
            pong_payload = origin[:8] + struct.pack(">q", now_us) if len(origin) >= 8 else b"\x00" * 16
            sock.sendall(encode_message(TYPE_PONG, bridge.next_seq(), 0, pong_payload))
        elif t == TYPE_FLOOR_TAKEN:
            log("  peer took the floor")
        elif t == TYPE_FLOOR_RELEASE:
            log("  peer released the floor")
        elif t == TYPE_END:
            log("  peer ended the call")
            break
        else:
            log(f"  {TYPE_NAMES.get(t, hex(t))} seq={m['seq']} len={len(m['payload'])}")

    bridge.stop()
    sock.close()
    log("Done.")


if __name__ == "__main__":
    main()
