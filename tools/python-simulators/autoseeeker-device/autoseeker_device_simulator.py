import socket
import threading
import time
import random
import sys
from datetime import datetime

class AutoseekerDeviceSimulator:
    def __init__(self, host="localhost", port=8888, device_id="8168000005"):
        self.host = host
        self.port = port
        self.device_id = device_id
        self.socket = None
        self.connected = False
        self.sending_data = False
        self.upload_interval = 10  # seconds (default)
        self.acc_status = 1  # 1=ACC ON, 0=ACC OFF
        self.fuel_cut = False
        self.fence_enabled = False
        self.current_location = {
            'lat': 22.582122,  # Default: 22°34.9273' = 22 + 34.9273/60
            'lon': 113.906633,  # Default: 113°54.3980' = 113 + 54.3980/60
            'speed': 0.0,
            'direction': 0,
            'valid': 'A'
        }
        self.vehicle_status = "F7FFBBFF"
        self.receive_thread = None
        self.data_thread = None
        self.command_handlers = self._initialize_command_handlers()

    def _initialize_command_handlers(self):
        """Initialize command handlers according to protocol"""
        return {
            'D1': self._handle_set_upload_interval,
            'S20': self._handle_cut_fuel,
            'SCF': self._handle_set_fence,
            'S71': self._handle_set_number,
            'R7': self._handle_clean_alarm,
            'LOCK': self._handle_lock_unlock,
            'MILE': self._handle_set_mileage,
            'R12': self._handle_heartbeat_confirmation
        }

    def connect(self):
        """Establish connection to the server"""
        try:
            self.socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.socket.settimeout(5)
            self.socket.connect((self.host, self.port))
            self.connected = True
            print(f"[+] Connected to server at {self.host}:{self.port}")

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

    def _send_data(self, data):
        """Send data to server"""
        if not self.connected or not self.socket:
            return False

        try:
            self.socket.sendall(data.encode())
            print(f"[AUTOSEEKER → SERVER] {data.strip()}")
            return True
        except Exception as e:
            print(f"[-] Failed to send data: {e}")
            self.connected = False
            return False

    def _receive_commands(self):
        """Thread function to receive commands from server"""
        buffer = ""
        while self.connected and self.socket:
            try:
                data = self.socket.recv(1024)
                if data:
                    buffer += data.decode()

                    # Process complete messages (ending with #)
                    while '#' in buffer:
                        message, buffer = buffer.split('#', 1)
                        message = message.strip()
                        if message.startswith('*'):
                            self._process_command(message + '#')
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
        print(f"[SERVER → AUTOSEEKER] {command.strip()}")

        try:
            # Remove * and #
            clean_data = command[1:-1]
            parts = clean_data.split(',')

            if len(parts) < 3:
                return

            # Get command code
            command_code = parts[2]

            if command_code in self.command_handlers:
                self.command_handlers[command_code](parts)
            else:
                print(f"[!] Unknown command: {command}")

        except Exception as e:
            print(f"[!] Error processing command: {e}")

    def _send_data_loop(self):
        """Thread function to periodically send data"""
        while self.connected:
            try:
                # Send heart pack data every upload_interval seconds
                time.sleep(self.upload_interval)
                self._send_heart_pack()

                # Occasionally send alarm if conditions are met
                if self._check_alarm_conditions():
                    self._send_alarm_packet()

            except Exception as e:
                print(f"[!] Error in data loop: {e}")
                time.sleep(5)

    def _send_heart_pack(self):
        """Send V1 heart pack data"""
        now = datetime.now()
        time_str = now.strftime("%H%M%S")
        date_str = now.strftime("%d%m%y")

        # Convert decimal coordinates to DDMM.MMMM format
        lat_deg = int(self.current_location['lat'])
        lat_min = (self.current_location['lat'] - lat_deg) * 60
        lat_str = f"{lat_deg:02d}{lat_min:05.2f}"

        lon_deg = int(self.current_location['lon'])
        lon_min = (self.current_location['lon'] - lon_deg) * 60
        lon_str = f"{lon_deg:03d}{lon_min:05.2f}"

        # Simulate network info
        mcc = "460"  # China
        mnc = "00"   # China Mobile
        lac = "10342"
        cell_id = random.randint(3000, 5000)

        # GPS and GSM signal strength
        gps_signal = random.randint(5, 15)
        gsm_signal = random.randint(20, 30)

        # Voltage (1=28V example)
        voltage = random.randint(25, 30)

        # Create heart pack V1 packet
        heart_pack = (f"*HQ,{self.device_id},V1,{time_str},{self.current_location['valid']},"
                     f"{lat_str},N,{lon_str},E,{self.current_location['speed']:05.2f},"
                     f"{self.current_location['direction']:03d},{date_str},{self.vehicle_status},"
                     f"{mcc},{mnc},{lac},{cell_id},{gps_signal},{gsm_signal},{voltage}#")

        self._send_data(heart_pack)

        # Update location with some movement
        self._update_location()

    def _send_alarm_packet(self):
        """Send alarm packet when alarm conditions are met"""
        now = datetime.now()
        time_str = now.strftime("%H%M%S")
        date_str = now.strftime("%d%m%y")

        # Convert coordinates
        lat_deg = int(self.current_location['lat'])
        lat_min = (self.current_location['lat'] - lat_deg) * 60
        lat_str = f"{lat_deg:02d}{lat_min:05.2f}"

        lon_deg = int(self.current_location['lon'])
        lon_min = (self.current_location['lon'] - lon_deg) * 60
        lon_str = f"{lon_deg:03d}{lon_min:05.2f}"

        # Different status for alarm
        alarm_status = "FBFBBFF"  # Alarm status bits

        alarm_pack = (f"*HQ,{self.device_id},V1,{time_str},{self.current_location['valid']},"
                     f"{lat_str},N,{lon_str},E,{self.current_location['speed']:05.2f},"
                     f"{self.current_location['direction']:03d},{date_str},{alarm_status},"
                     f"460,00,10342,4283,10,25,128#")

        self._send_data(alarm_pack)
        print("[!] Alarm packet sent")

    def _update_location(self):
        """Simulate movement by updating location"""
        # Small random movement
        if random.random() > 0.3:  # 70% chance of movement
            self.current_location['lat'] += random.uniform(-0.001, 0.001)
            self.current_location['lon'] += random.uniform(-0.001, 0.001)
            self.current_location['speed'] = random.uniform(0, 80)
            self.current_location['direction'] = random.randint(0, 359)
            self.current_location['valid'] = 'A'
        else:
            # Stationary
            self.current_location['speed'] = 0
            self.current_location['valid'] = 'A'

    def _check_alarm_conditions(self):
        """Check if alarm conditions are met"""
        # Randomly trigger alarms occasionally
        return random.random() > 0.95  # 5% chance

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

        # Build response based on command type
        if command_code == "D1":
            response = (f"*HQ,{self.device_id},V4,D1,{original_parts[4]},65535,"
                       f"{original_time},{response_time},{self.current_location['valid']},"
                       f"{lat_str},N,{lon_str},E,{self.current_location['speed']:05.2f},"
                       f"{self.current_location['direction']:03d},{date_str},FFFFBBFF,"
                       f"460,00,10342,3721#")
        elif command_code == "S20":
            response = (f"*HQ,{self.device_id},V4,S20,{status},{original_time},{response_time},"
                       f"{self.current_location['valid']},{lat_str},N,{lon_str},E,"
                       f"{self.current_location['speed']:05.2f},{self.current_location['direction']:03d},"
                       f"{date_str}F7FFBBFF,460,00,10342,3721#")
        elif command_code == "SCF":
            response = (f"*HQ,{self.device_id},V4,SCF,{original_time},{response_time},"
                       f"{self.current_location['valid']},{lat_str},N,{lon_str},E,"
                       f"{self.current_location['speed']:05.2f},{self.current_location['direction']:03d},"
                       f"{date_str},FFFFB9FF,460,00,10342,3721#")
        else:
            response = (f"*HQ,{self.device_id},V4,{command_code},{original_time},{response_time},"
                       f"{self.current_location['valid']},{lat_str},N,{lon_str},E,"
                       f"{self.current_location['speed']:05.2f},{self.current_location['direction']:03d},"
                       f"{date_str},FFFFFBFF,460,00,10342,3721#")

        self._send_data(response)

    # Command Handlers
    def _handle_set_upload_interval(self, parts):
        """Handle D1: Set GPRS Upload Interval Time"""
        interval = int(parts[4])
        acc_status = parts[5] if len(parts) > 5 else "1"

        self.upload_interval = interval
        self.acc_status = int(acc_status)

        print(f"[+] Upload interval set to: {interval} seconds (ACC={acc_status})")
        self._send_response(parts)

    def _handle_cut_fuel(self, parts):
        """Handle S20: Cut Fuel command"""
        if len(parts) > 5 and parts[5] == "0":
            self.fuel_cut = False
            print("[+] Fuel restored/engine ON")
            self._send_response(parts, "OK")
        else:
            self.fuel_cut = True
            # Parse fuel cut pattern
            pattern = parts[5:] if len(parts) > 5 else []
            print(f"[+] Fuel cut activated with pattern: {pattern}")
            self._send_response(parts, "DONE")

    def _handle_set_fence(self, parts):
        """Handle SCF: Set/Clear Fence"""
        if len(parts) > 4 and parts[4] == "0":
            self.fence_enabled = False
            print("[+] Fence disabled")
        else:
            self.fence_enabled = True
            print("[+] Fence enabled")
        self._send_response(parts)

    def _handle_set_number(self, parts):
        """Handle S71: Set Pre-saved/SOS Number"""
        number_type = parts[4]  # 01=pre-saved, 02=SOS
        numbers = parts[5:]

        if number_type == "01":
            print(f"[+] Pre-saved numbers set: {numbers}")
        else:
            print(f"[+] SOS numbers set: {numbers}")

        self._send_response(parts)

    def _handle_clean_alarm(self, parts):
        """Handle R7: Clean Alarm Command"""
        print("[+] Alarms cleared")
        self._send_response(parts)

    def _handle_lock_unlock(self, parts):
        """Handle LOCK: Lock/Unlock Command"""
        if len(parts) > 4 and parts[4] == "0":
            print("[+] Vehicle unlocked")
        else:
            print("[+] Vehicle locked")
        self._send_response(parts)

    def _handle_set_mileage(self, parts):
        """Handle MILE: Set Mileage Command"""
        if len(parts) > 4:
            mileage = parts[4]
            print(f"[+] Mileage set to: {mileage}")
        self._send_response(parts)

    def _handle_heartbeat_confirmation(self, parts):
        """Handle R12: Heartbeat Confirmation"""
        # Server confirms receipt of heart pack
        print(f"[+] Heartbeat confirmed by server")
        # No response needed for R12

    def run(self):
        """Run the device simulator"""
        print("\n" + "="*50)
        print("AUTOSEEKER DEVICE SIMULATOR")
        print("="*50)
        print(f"Device ID: {self.device_id}")
        print(f"Server: {self.host}:{self.port}")
        print(f"Default upload interval: {self.upload_interval}s")
        print("="*50)

        if not self.connect():
            print("[-] Failed to connect to server. Exiting.")
            return

        print("\n[+] Device is running. Press Ctrl+C to stop.")
        print("[+] Sending heart pack data and responding to commands...")

        try:
            # Keep main thread alive
            while self.connected:
                time.sleep(1)

        except KeyboardInterrupt:
            print("\n[!] Stopping device...")
        finally:
            self.disconnect()
            print("[+] Device stopped.")

def main():
    # Configuration
    HOST = "localhost"  # Change to your server IP
    PORT = 8888         # Change to your server port
    DEVICE_ID = "8168000005"  # Change to your device ID

    # Create and run device simulator
    device = AutoseekerDeviceSimulator(HOST, PORT, DEVICE_ID)
    device.run()

if __name__ == "__main__":
    main()