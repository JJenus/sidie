import socket
import threading
import time
import random
import sys
from datetime import datetime

class GT06DeviceSimulatorFixed:
    def __init__(self, host="localhost", port=8888, imei="865205030330012"):
        self.host = host
        self.port = port
        self.imei = imei
        self.socket = None
        self.connected = False
        self.sending_data = False
        self.gprs_interval = 5  # seconds
        self.working_mode = 0  # 0: real-time, 1: power saving, 2: deep sleep
        self.current_location = {
            'lat': 22.675865,  # Default latitude (converted from 2240.55181)
            'lon': 113.972065,  # Default longitude (converted from 11358.32389)
            'speed': 0.0,
            'direction': 0,
            'valid': 'A'
        }
        self.battery_percent = 100
        self.vehicle_status = "FFFFFBFF"
        self.receive_thread = None
        self.data_thread = None
        self.command_handlers = self._initialize_command_handlers()

    def _initialize_command_handlers(self):
        """Initialize command handlers according to protocol"""
        return {
            'S1': self._handle_change_password,
            'S2': self._handle_set_center_number,
            'S3': self._handle_set_admin_number,
            'S18': self._handle_set_alarm_mode,
            'S19': self._handle_alarm_type_setting,
            'S20': self._handle_remote_disable_fuel,
            'S21': self._handle_set_geo_fence,
            'S23': self._handle_set_ip_port,
            'S24': self._handle_set_apn,
            'S25': self._handle_factory_default,
            'S26': self._handle_read_device_state,
            'S33': self._handle_overspeed_setting,
            'S80': self._handle_check_lbs,
            'D1': self._handle_set_gprs_interval,
            'D2': self._handle_fast_locate,
            'R1': self._handle_restart,
            'WKMD': self._handle_change_working_mode,
            'SLP': self._handle_sleep_mode,
            'V0': self._handle_login,
            'HTBT': self._handle_heartbeat
        }

    def connect(self):
        """Establish connection to the server"""
        try:
            self.socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.socket.settimeout(5)
            self.socket.connect((self.host, self.port))
            self.connected = True
            print(f"[+] Connected to server at {self.host}:{self.port}")

            # Send login packet
            self._send_login()

            # Start receiving thread
            self.receive_thread = threading.Thread(target=self._receive_commands, daemon=True)
            self.receive_thread.start()

            # Start data sending thread
            self.data_thread = threading.Thread(target=self._send_data_loop, daemon=True)
            self.data_thread.start()

            return True
        except Exception as e:
            print(f"[-] Connection failed: {e}")
            return False

    def disconnect(self):
        """Close the connection"""
        self.connected = False
        self.sending_data = False

        if self.socket:
            try:
                self.socket.close()
            except:
                pass
            self.socket = None

        print("[+] Disconnected from server")

    def _send_login(self):
        """Send login packet to server"""
        login_packet = f"*HQ,{self.imei},V0#"
        self._send_data(login_packet)

    def _send_data(self, data):
        """Send data to server"""
        if not self.connected or not self.socket:
            return False

        try:
            # DEBUG: Check what we're about to send
            data_to_send = data
            if not data_to_send.endswith('#'):
                print(f"[WARNING] Data doesn't end with #: {data_to_send}")
                data_to_send = data_to_send + '#'

            # Send the raw bytes
            bytes_sent = self.socket.sendall(data_to_send.encode())

            # DEBUG: Print what we sent with byte count
            byte_count = len(data_to_send.encode())
            print(f"[DEVICE → SERVER] ({byte_count} bytes) {data_to_send}")

            return True
        except Exception as e:
            print(f"[-] Failed to send data: {e}")
            self.connected = False
            return False

    def _receive_commands(self):
        """Thread function to receive commands from server"""
        buffer = bytearray()
        while self.connected and self.socket:
            try:
                data = self.socket.recv(1024)
                if data:
                    buffer.extend(data)

                    # Convert to string for processing
                    message = buffer.decode('ascii', errors='ignore')

                    # Process complete messages (ending with #)
                    while '#' in message:
                        msg_end = message.find('#') + 1
                        full_message = message[:msg_end]
                        message = message[msg_end:]
                        buffer = bytearray(message.encode())

                        if full_message.startswith('*'):
                            print(f"[SERVER → DEVICE] {full_message.strip()}")
                            self._process_command(full_message)
                else:
                    # Connection closed
                    self.connected = False
                    print("[-] Connection closed by server")
                    break

            except socket.timeout:
                continue
            except Exception as e:
                print(f"[-] Receive error: {e}")
                self.connected = False
                break

    def _process_command(self, command):
        """Process incoming command from server"""
        try:
            # Remove * and #
            clean_data = command[1:-1]
            parts = clean_data.split(',')

            if len(parts) < 3:
                return

            # Get command code (could be in different positions)
            command_code = None
            if parts[2] in self.command_handlers:
                command_code = parts[2]
            elif len(parts) > 3 and parts[3] in self.command_handlers:
                command_code = parts[3]

            if command_code and command_code in self.command_handlers:
                self.command_handlers[command_code](parts)
            else:
                print(f"[!] Unknown command: {command}")

        except Exception as e:
            print(f"[!] Error processing command: {e}")

    def _send_data_loop(self):
        """Thread function to periodically send data"""
        while self.connected:
            try:
                # Only send data if not in deep sleep or based on working mode
                if self.working_mode == 0:  # Real-time mode
                    time.sleep(self.gprs_interval)
                    self._send_gps_data()
                elif self.working_mode == 1:  # Power saving mode
                    # Simulate movement-based updates
                    if random.random() > 0.7:  # 30% chance of movement
                        self._update_location_movement()
                        self._send_gps_data()
                    time.sleep(60)  # Check every minute
                elif self.working_mode == 2:  # Deep sleep mode
                    # Only wake up on movement
                    time.sleep(300)  # Check every 5 minutes

                # Send heartbeat periodically
                if random.random() > 0.8:  # 20% chance to send heartbeat
                    self._send_heartbeat()

            except Exception as e:
                print(f"[!] Error in data loop: {e}")
                time.sleep(5)

    def _send_gps_data(self):
        """Send GPS data packet"""
        now = datetime.now()
        time_str = now.strftime("%H%M%S")
        date_str = now.strftime("%d%m%y")

        # Convert decimal coordinates to DDMM.MMMMM format
        lat_deg = int(self.current_location['lat'])
        lat_min = (self.current_location['lat'] - lat_deg) * 60
        lat_str = f"{lat_deg:02d}{lat_min:05.2f}"

        lon_deg = int(self.current_location['lon'])
        lon_min = (self.current_location['lon'] - lon_deg) * 60
        lon_str = f"{lon_deg:03d}{lon_min:05.2f}"

        # Create GPS data packet - MAKE SURE IT ENDS WITH #
        gps_packet = (f"*HQ,{self.imei},V1,{time_str},{self.current_location['valid']},"
                     f"{lat_str},N,{lon_str},E,{self.current_location['speed']:05.2f},"
                     f"{self.current_location['direction']:03d},{date_str},{self.vehicle_status}#")

        self._send_data(gps_packet)

    def _send_heartbeat(self):
        """Send heartbeat packet"""
        if self.imei.startswith('86520503'):  # G200 model
            heartbeat = f"*HQ,{self.imei},HTBT,{self.battery_percent}#"
        else:
            heartbeat = f"*HQ,{self.imei},HTBT#"

        self._send_data(heartbeat)

    def _update_location_movement(self):
        """Simulate movement by updating location"""
        # Small random movement
        self.current_location['lat'] += random.uniform(-0.001, 0.001)
        self.current_location['lon'] += random.uniform(-0.001, 0.001)
        self.current_location['speed'] = random.uniform(0, 60)
        self.current_location['direction'] = random.randint(0, 359)
        self.current_location['valid'] = 'A'

    def _send_response(self, original_parts, status="DONE"):
        """Send V4 response for command confirmation"""
        now = datetime.now()
        response_time = now.strftime("%H%M%S")
        original_time = original_parts[3] if len(original_parts) > 3 else response_time

        # Convert coordinates for response
        lat_deg = int(self.current_location['lat'])
        lat_min = (self.current_location['lat'] - lat_deg) * 60
        lat_str = f"{lat_deg:02d}{lat_min:04.1f}"

        lon_deg = int(self.current_location['lon'])
        lon_min = (self.current_location['lon'] - lon_deg) * 60
        lon_str = f"{lon_deg:03d}{lon_min:04.1f}"

        date_str = now.strftime("%d%m%y")

        command_code = original_parts[2]
        response = (f"*HQ,{self.imei},V4,{command_code},{status},{original_time},"
                   f"{response_time},{self.current_location['valid']},{lat_str},N,"
                   f"{lon_str},E,{self.current_location['speed']:05.2f},"
                   f"{self.current_location['direction']:03d},{date_str},{self.vehicle_status}#")

        self._send_data(response)

    # Command Handlers (same as before)
    def _handle_change_password(self, parts):
        """Handle S1: Change Password"""
        print(f"[+] Password changed from {parts[4]} to {parts[5]}")
        self._send_response(parts)

    def _handle_set_center_number(self, parts):
        """Handle S2: Set Center Number"""
        center_number = parts[4]
        print(f"[+] Center number set to: {center_number}")
        self._send_response(parts)

    def _handle_set_admin_number(self, parts):
        """Handle S3: Set Admin Number"""
        admin_numbers = parts[4:]
        print(f"[+] Admin numbers set: {admin_numbers}")
        self._send_response(parts)

    def _handle_set_alarm_mode(self, parts):
        """Handle S18: Set Alarm Mode"""
        mode = parts[4]
        modes = {0: "Close SMS and Calling alarm",
                 1: "SMS alarm",
                 2: "Calling center number as alarm"}
        print(f"[+] Alarm mode set to: {mode} ({modes.get(int(mode), 'Unknown')})")
        self._send_response(parts)

    def _handle_alarm_type_setting(self, parts):
        """Handle S19: Alarm Type Setting"""
        alarm_type = parts[4]
        enable = parts[5]
        alarm_types = {0: "Power cut", 1: "ACC", 2: "Low battery",
                       3: "Vibrate", 4: "Removal"}
        status = "Enabled" if enable == "1" else "Disabled"
        print(f"[+] {alarm_types.get(int(alarm_type), 'Unknown')} alarm {status}")
        self._send_response(parts)

    def _handle_remote_disable_fuel(self, parts):
        """Handle S20: Remote Disable Fuel or Electricity"""
        if len(parts) > 5 and parts[5] == "0":
            print("[+] Fuel enabled")
            self._send_response(parts, "OK")
        else:
            print("[+] Fuel disabled")
            self._send_response(parts, "DONE")

    def _handle_set_geo_fence(self, parts):
        """Handle S21: Set Geo-fence"""
        radius = parts[4]
        mode = parts[5]
        modes = {1: "Out fence alarm", 2: "In fence alarm", 3: "Out and In fence alarm"}
        print(f"[+] Geo-fence set: radius={radius}m, mode={mode} ({modes.get(int(mode), 'Unknown')})")
        self._send_response(parts, "DONE")

    def _handle_set_ip_port(self, parts):
        """Handle S23: Set IP Port"""
        ip = '.'.join(parts[4:8])
        port = parts[8]
        print(f"[+] IP/Port set to: {ip}:{port}")
        self._send_response(parts)

    def _handle_set_apn(self, parts):
        """Handle S24: Set APN"""
        apn = parts[4]
        print(f"[+] APN set to: {apn}")
        self._send_response(parts)

    def _handle_factory_default(self, parts):
        """Handle S25: Factory default settings"""
        print("[+] Factory defaults restored")
        self._send_response(parts)

    def _handle_read_device_state(self, parts):
        """Handle S26: Read device's state"""
        check_type = parts[4]
        now = datetime.now()

        if check_type == "0":  # Basic data
            response = (f"*HQ,{self.imei},V4,S26,{parts[3]},{now.strftime('%H%M%S')},"
                       f"CMNET,,,13812341234,1,100,{self.gprs_interval},8,{self.battery_percent}#")
        elif check_type == "1":  # Software version
            response = (f"*HQ,{self.imei},V4,S26,{parts[3]},{now.strftime('%H%M%S')},"
                       f"GW61D_ZDR_TK102_V2.6.2,2016/07/28 21:16#")
        else:  # Other data
            response = (f"*HQ,{self.imei},V4,S26,{parts[3]},{now.strftime('%H%M%S')},"
                       f"Additional device info#")

        self._send_data(response)

    def _handle_overspeed_setting(self, parts):
        """Handle S33: Overspeed setting"""
        speed_limit = parts[4]
        if speed_limit == "0":
            print("[+] Overspeed alarm disabled")
        else:
            print(f"[+] Overspeed limit set to: {speed_limit} km/h")
        self._send_response(parts)

    def _handle_check_lbs(self, parts):
        """Handle S80: Check LBS"""
        now = datetime.now()
        response = (f"*HQ,{self.imei},V4,S80,{parts[3]},{now.strftime('%H%M%S')},"
                   f"460,00,03,009350,004022,009350,004032,009350,004031#")
        self._send_data(response)

    def _handle_set_gprs_interval(self, parts):
        """Handle D1: Set GPRS interval time"""
        interval = int(parts[4])
        self.gprs_interval = interval
        print(f"[+] GPRS interval set to: {interval} seconds")
        self._send_response(parts)

    def _handle_fast_locate(self, parts):
        """Handle D2: Fast Locate"""
        gps_time = parts[4]
        print(f"[+] Fast locate activated for {gps_time} seconds")
        self._send_response(parts)

    def _handle_restart(self, parts):
        """Handle R1: Restart command"""
        print("[+] Device restarting...")
        self._send_response(parts)
        time.sleep(2)
        print("[+] Device restarted")
        self._send_login()

    def _handle_change_working_mode(self, parts):
        """Handle WKMD: Change working mode"""
        mode = parts[4]
        self.working_mode = int(mode)
        modes = {0: "GPS Real-time Tracking", 1: "LBS Power saving", 2: "GPS Intelligent"}
        print(f"[+] Working mode changed to: {mode} ({modes.get(self.working_mode, 'Unknown')})")
        self._send_response(parts)

    def _handle_sleep_mode(self, parts):
        """Handle SLP: Sleep mode (G200)"""
        mode = parts[4]
        self.working_mode = int(mode)
        modes = {0: "Real-time Tracking", 1: "Power saving", 2: "Deep sleep"}
        print(f"[+] Sleep mode changed to: {mode} ({modes.get(self.working_mode, 'Unknown')})")
        self._send_response(parts)

    def _handle_login(self, parts):
        """Handle V0: Login"""
        response = f"*HQ,{self.imei},V0#"
        self._send_data(response)

    def _handle_heartbeat(self, parts):
        """Handle HTBT: Heartbeat"""
        response = f"*HQ,{self.imei},HTBT#"
        self._send_data(response)

    def run(self):
        """Run the device simulator"""
        print("\n" + "="*50)
        print("GT06 DEVICE SIMULATOR (FIXED VERSION)")
        print("="*50)
        print(f"IMEI: {self.imei}")
        print(f"Server: {self.host}:{self.port}")
        print("="*50)

        if not self.connect():
            print("[-] Failed to connect to server. Exiting.")
            return

        print("\n[+] Device is running. Press Ctrl+C to stop.")
        print("[+] Sending movement data and responding to commands...")
        print("[DEBUG] Check that each message ends with #")

        try:
            # Keep main thread alive
            while self.connected:
                time.sleep(1)

                # Simulate occasional movement
                if random.random() > 0.9 and self.working_mode == 0:
                    self._update_location_movement()

        except KeyboardInterrupt:
            print("\n[!] Stopping device...")
        finally:
            self.disconnect()
            print("[+] Device stopped.")

def main():
    # Configuration
    HOST = "localhost"  # Change to your server IP
    PORT = 8888         # Change to your server port
    IMEI = "865205030330012"  # Change to your device IMEI

    # Create and run device simulator
    device = GT06DeviceSimulatorFixed(HOST, PORT, IMEI)
    device.run()

if __name__ == "__main__":
    main()