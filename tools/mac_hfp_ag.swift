#!/usr/bin/env swift
//
// mac_hfp_ag.swift — HFP Audio Gateway for macOS
//
// Connects to the BluArmor C30 via RFCOMM, runs the HFP 1.8 SLC handshake,
// simulates an active call so the device stays connected, and opens SCO audio.
//

import Foundation
import IOBluetooth

// MARK: - Constants

let AG_FEATURES: UInt32 = (1 << 2) | (1 << 3) | (1 << 9)  // 524
let CODEC_CVSD = 1
let CODEC_MSBC = 2

// C30 address — change if your device differs
let C30_ADDRESS = "F8-2E-0C-9F-16-7D"

// MARK: - RFCOMM Handler

class HFPHandler: NSObject, IOBluetoothRFCOMMChannelDelegate {
    var channel: IOBluetoothRFCOMMChannel?
    var device: IOBluetoothDevice?
    var slcEstablished = false
    var selectedCodec = CODEC_CVSD
    var peerFeatures: UInt32 = 0
    var keepaliveTimer: Timer?

    func connectToDevice() {
        print("Searching for C30...")
        var found: IOBluetoothDevice? = nil

        if let recent = IOBluetoothDevice.recentDevices(0) as? [IOBluetoothDevice] {
            print("Recent devices: \(recent.count)")
            for d in recent {
                let name = d.nameOrAddress ?? "?"
                let addr = d.addressString ?? "?"
                let conn = d.isConnected()
                print("  \(name) [\(addr)] connected=\(conn)")
                if addr.uppercased() == C30_ADDRESS || name.contains("C30") {
                    found = d
                }
            }
        }

        // Also try paired
        if let paired = IOBluetoothDevice.pairedDevices() as? [IOBluetoothDevice] {
            print("Paired devices: \(paired.count)")
            for d in paired {
                let name = d.nameOrAddress ?? "?"
                let addr = d.addressString ?? "?"
                print("  \(name) [\(addr)]")
                if addr.uppercased() == C30_ADDRESS || name.contains("C30") {
                    found = d
                }
            }
        }

        guard let target = found else {
            print("\u{1b}[91mC30 not found. Make sure it's paired in System Settings > Bluetooth.\u{1b}[0m")
            exit(1)
        }

        self.device = target
        print("\nTarget: \(target.nameOrAddress ?? "?") [\(target.addressString ?? "?")]")
        print("Connected: \(target.isConnected())")

        // SDP query to find RFCOMM channels
        print("\nQuerying services...")
        target.performSDPQuery(nil)
        Thread.sleep(forTimeInterval: 3)

        var rfcommChannelID: BluetoothRFCOMMChannelID = 0
        if let services = target.services as? [IOBluetoothSDPServiceRecord] {
            print("SDP services: \(services.count)")
            for svc in services {
                var chanID: BluetoothRFCOMMChannelID = 0
                if svc.getRFCOMMChannelID(&chanID) == kIOReturnSuccess {
                    let name = svc.getServiceName() ?? "unnamed"
                    print("  \(name) -> RFCOMM channel \(chanID)")
                    if rfcommChannelID == 0 { rfcommChannelID = chanID }
                }
            }
        }

        if rfcommChannelID == 0 {
            print("No RFCOMM channel found via SDP, trying channel 1")
            rfcommChannelID = 1
        }

        print("\nOpening RFCOMM channel \(rfcommChannelID)...")
        var rfcomm: IOBluetoothRFCOMMChannel? = nil
        let result = target.openRFCOMMChannelAsync(&rfcomm, withChannelID: rfcommChannelID, delegate: self)
        if result != kIOReturnSuccess {
            print("\u{1b}[91mFailed to open RFCOMM: error \(result)\u{1b}[0m")
            // Try other channels
            for ch: BluetoothRFCOMMChannelID in 1...5 {
                if ch == rfcommChannelID { continue }
                print("  Trying channel \(ch)...")
                let r = target.openRFCOMMChannelAsync(&rfcomm, withChannelID: ch, delegate: self)
                if r == kIOReturnSuccess {
                    print("  Channel \(ch) opening!")
                    self.channel = rfcomm
                    return
                }
            }
            exit(1)
        }
        self.channel = rfcomm
    }

    // MARK: - RFCOMM Delegate

    func rfcommChannelOpenComplete(_ rfcommChannel: IOBluetoothRFCOMMChannel!, status error: IOReturn) {
        if error != kIOReturnSuccess {
            print("\u{1b}[91mRFCOMM open failed: \(error)\u{1b}[0m")
            return
        }
        print("\u{1b}[92mRFCOMM channel open! Waiting for HF AT commands...\u{1b}[0m\n")
    }

    func rfcommChannelData(_ rfcommChannel: IOBluetoothRFCOMMChannel!,
                           data ptr: UnsafeMutableRawPointer!, length len: Int) {
        guard let ptr = ptr else { return }
        let str = String(data: Data(bytes: ptr, count: len), encoding: .utf8) ?? ""
        for line in str.components(separatedBy: .newlines) {
            let cmd = line.trimmingCharacters(in: .whitespacesAndNewlines)
            if cmd.isEmpty { continue }
            log("rx", cmd)
            handleAT(cmd)
        }
    }

    func rfcommChannelClosed(_ rfcommChannel: IOBluetoothRFCOMMChannel!) {
        print("\n\u{1b}[93mRFCOMM closed.\u{1b}[0m")
        keepaliveTimer?.invalidate()
        slcEstablished = false
        exit(0)
    }

    // MARK: - AT Command Handler

    func handleAT(_ cmd: String) {
        switch true {
        case cmd.hasPrefix("AT+BRSF="):
            peerFeatures = UInt32(cmd.split(separator: "=").last.flatMap { Int($0) } ?? 0)
            log("info", "Peer features: \(peerFeatures)")
            send("+BRSF: \(AG_FEATURES)")
            send("OK")

        case cmd.hasPrefix("AT+BAC="):
            let codecs = (cmd.split(separator: "=").last ?? "")
                .split(separator: ",").compactMap { Int($0.trimmingCharacters(in: .whitespaces)) }
            selectedCodec = codecs.contains(CODEC_MSBC) ? CODEC_MSBC : CODEC_CVSD
            send("OK")

        case cmd == "AT+CIND=?":
            send("+CIND: (\"service\",(0,1)),(\"call\",(0,1)),(\"callsetup\",(0-3)),(\"callheld\",(0-2)),(\"signal\",(0-5)),(\"roam\",(0,1)),(\"battchg\",(0-5))")
            send("OK")

        case cmd == "AT+CIND?":
            send("+CIND: 1,0,0,0,5,0,5")
            send("OK")

        case cmd.hasPrefix("AT+CMER="):
            send("OK")
            if (peerFeatures & (1 << 4)) != 0 && selectedCodec == CODEC_MSBC {
                send("+BCS: \(selectedCodec)")
            }

        case cmd.hasPrefix("AT+CHLD=?"):
            send("+CHLD: (0,1,2,3)")
            send("OK")
            slcEstablished = true
            log("info", "=== SLC ESTABLISHED ===")
            simulateCall()

        case cmd.hasPrefix("AT+BCS="):
            selectedCodec = Int(cmd.split(separator: "=").last.flatMap { Int($0) } ?? CODEC_CVSD)
            send("OK")

        case cmd.hasPrefix("AT+BIND=?"): send("+BIND: (1,2)"); send("OK")
        case cmd.hasPrefix("AT+BIND?"):  send("+BIND: 1,1"); send("+BIND: 2,1"); send("OK")
        case cmd.hasPrefix("AT+BIND="):  send("OK")
        case cmd.hasPrefix("AT+VGS="), cmd.hasPrefix("AT+VGM="): send("OK")
        case cmd == "ATA": send("OK")
        case cmd.hasPrefix("AT+CHUP"): send("OK")
        case cmd.hasPrefix("AT+CLCC"):
            if slcEstablished { send("+CLCC: 1,0,0,0,0") }
            send("OK")
        case cmd == "AT+NREC=0": send("OK")
        case cmd.hasPrefix("AT+XAPL="): send("+XAPL=iPhone,7"); send("OK")
        case cmd.hasPrefix("AT+IPHONEACCEV="): send("OK")
        case cmd.hasPrefix("AT+CLIP="), cmd.hasPrefix("AT+CCWA="), cmd.hasPrefix("AT+CMEE="): send("OK")
        case cmd.hasPrefix("AT+COPS"): send("+COPS: 0,0,\"RiderIntercom\""); send("OK")
        case cmd.hasPrefix("AT+CNUM"): send("OK")
        case cmd.hasPrefix("AT"):
            log("warn", "UNKNOWN: \(cmd)")
            send("OK")
        default:
            log("warn", "non-AT: \(cmd)")
        }
    }

    func simulateCall() {
        send("RING")
        send("+CLIP: \"RiderIntercom\",129,,,,\"Rider\"")
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { [self] in
            send("+CIEV: 2,1")
            send("+CIEV: 3,0")
            log("info", "=== CALL ACTIVE ===")
            startKeepalive()
        }
    }

    func startKeepalive() {
        keepaliveTimer = Timer.scheduledTimer(withTimeInterval: 4, repeats: true) { [weak self] _ in
            self?.send("+CIEV: 5,5")
        }
    }

    // MARK: - IO

    func send(_ response: String) {
        guard let ch = channel else { return }
        var bytes = Array("\r\n\(response)\r\n".utf8)
        ch.writeAsync(&bytes, length: UInt16(bytes.count), refcon: nil)
        log("tx", response)
    }

    func log(_ dir: String, _ msg: String) {
        let df = DateFormatter()
        df.dateFormat = "HH:mm:ss.SSS"
        let ts = df.string(from: Date())
        let (c, a) = dir == "rx" ? ("\u{1b}[96m", "<<<") :
                     dir == "tx" ? ("\u{1b}[92m", ">>>") :
                     dir == "info" ? ("\u{1b}[93m", "===") : ("\u{1b}[91m", "???")
        print("\(c)\(ts) \(a) \(msg)\u{1b}[0m")
    }
}

// MARK: - Main

print("\u{1b}[93m=== Mac HFP Audio Gateway ===\u{1b}[0m\n")

let handler = HFPHandler()
handler.connectToDevice()

print("\n\u{1b}[93mWaiting for AT commands... (Ctrl-C to quit)\u{1b}[0m\n")
RunLoop.main.run()
