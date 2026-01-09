import socket
import threading
import time
import sys
import select

class TrackerCommandSender:
    def __init__(self, host="localhost", port=8888):
        self.host = host
        self.port = port
        self.socket = None
        self.connected = False
        self.auto_send = False
        self.send_interval = 4  # Default interval in seconds
        self.commands = self._get_default_commands()
        self.current_command_index = 0
        self.receive_thread = None

    def _get_default_commands(self):
        """Return the list of default commands from the protocol"""
        return [
            # 1. Heartbeat acknowledgment command
            "*HQ,8168000008,R12,043602#",

            # 2. Set GPRS upload interval command D1
            "*HQ,8168000005,D1,062108,30,1#",

            # 3. Cut fuel/power command S20
            "*HQ,8168000005,S20,061158,1,3,10,3,5,5,3,5,3,5,3,5#",
            "*HQ,8168000005,S20,061713,0,0#",

            # 4. Set electronic fence command SCF
            "*HQ,8168000005,SCF,061837,0,0#",  # Disable fence
            "*HQ,8168000005,SCF,061939,1,1#",  # Enable fence

            # 5. Set pre-saved number command S71
            "*HQ,8168000005,S71,062328,01,18688993050#",

            # 6. Set SOS number command S71
            "*HQ,8168000005,S71,063012,02,18600000001,18600000002#",

            # 7. Clear alarm command R7
            "*HQ,8168000005,R7,063012#",

            # 8. Unlock/Lock command LOCK
            "*HQ,8168000005,LOCK,061837,0#",  # Unlock
            "*HQ,8168000005,LOCK,061939,1#",  # Lock

            # 9. Set mileage command MILE
            "*HQ,8168000005,MILE,061837,100#",

            # 10. SMS command
            "admin123456 13888888888",

            # 11. Command feedback example (V4 response)
            "*HQ,8168000005,V4,S20,DONE,061158,061116,A,2235.0086,N,11354.3668,E,000.00,000,160716F7FFBBFF,460,00,10342,3721#",
        ]

    def connect(self):
        """Establish connection to the server"""
        try:
            self.socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.socket.settimeout(5)
            self.socket.connect((self.host, self.port))
            self.connected = True
            print(f"[+] Connected to {self.host}:{self.port}")

            # Start receiving thread
            self.receive_thread = threading.Thread(target=self._receive_data, daemon=True)
            self.receive_thread.start()

            return True
        except Exception as e:
            print(f"[-] Connection failed: {e}")
            return False

    def disconnect(self):
        """Close the connection"""
        self.connected = False
        self.auto_send = False

        if self.socket:
            try:
                self.socket.close()
            except:
                pass
            self.socket = None

        print("[+] Disconnected")

    def send_command(self, command):
        """Send a single command"""
        if not self.connected or not self.socket:
            print("[-] Not connected")
            return False

        try:
            # Add newline if not present
            if not command.endswith('\n'):
                command += '\n'

            self.socket.sendall(command.encode())
            print(f"[+] Sent: {command.strip()}")
            return True
        except Exception as e:
            print(f"[-] Failed to send command: {e}")
            self.connected = False
            return False

    def _receive_data(self):
        """Thread function to receive data from server"""
        while self.connected and self.socket:
            try:
                # Check if data is available
                ready = select.select([self.socket], [], [], 0.5)
                if ready[0]:
                    data = self.socket.recv(1024)
                    if data:
                        print(f"[+] Received: {data.decode().strip()}")
                    else:
                        # Connection closed
                        self.connected = False
                        print("[-] Connection closed by server")
            except socket.timeout:
                continue
            except Exception:
                self.connected = False
                break

    def start_auto_send(self, interval=None):
        """Start automatic sending of commands from the list"""
        if interval:
            self.send_interval = max(3, min(5, interval))  # Clamp between 3-5 seconds

        if not self.auto_send:
            self.auto_send = True
            print(f"[+] Auto-send started with {self.send_interval}s interval")

            def auto_send_loop():
                while self.auto_send and self.connected:
                    if self.current_command_index >= len(self.commands):
                        self.current_command_index = 0

                    command = self.commands[self.current_command_index]
                    self.send_command(command)
                    self.current_command_index += 1

                    # Sleep for the interval
                    for _ in range(self.send_interval * 10):  # Check every 0.1s
                        if not self.auto_send:
                            break
                        time.sleep(0.1)

            threading.Thread(target=auto_send_loop, daemon=True).start()
        else:
            print("[!] Auto-send is already running")

    def stop_auto_send(self):
        """Stop automatic sending"""
        if self.auto_send:
            self.auto_send = False
            print("[+] Auto-send stopped")

    def set_interval(self, interval):
        """Change the sending interval (3-5 seconds)"""
        interval = float(interval)
        if 3 <= interval <= 5:
            self.send_interval = interval
            print(f"[+] Send interval set to {interval} seconds")
        else:
            print(f"[!] Interval must be between 3 and 5 seconds")

    def print_help(self):
        """Display available commands"""
        print("\n" + "="*50)
        print("COMMAND MENU")
        print("="*50)
        print("send <command>   - Send a custom command")
        print("auto start       - Start auto-sending commands (3-5s interval)")
        print("auto stop        - Stop auto-sending")
        print("interval <3-5>   - Set auto-send interval (3-5 seconds)")
        print("list             - Show available commands")
        print("next             - Send next command from list")
        print("status           - Show connection and auto-send status")
        print("help             - Show this help menu")
        print("exit             - Disconnect and exit")
        print("="*50)

    def print_command_list(self):
        """Display the list of available commands"""
        print("\n" + "="*50)
        print("AVAILABLE COMMANDS")
        print("="*50)
        for i, cmd in enumerate(self.commands, 1):
            print(f"{i:2d}. {cmd}")
        print("="*50)
        print(f"Next command to send: #{self.current_command_index + 1}")

    def run_interactive(self):
        """Run the interactive command interface"""
        print("\n" + "="*50)
        print("TRACKER COMMAND SENDER")
        print("="*50)
        print(f"Target: {self.host}:{self.port}")
        print("Type 'help' for available commands")
        print("="*50)

        # Connect to server
        if not self.connect():
            print("[-] Failed to connect. Exiting.")
            return

        try:
            while self.connected:
                # Check for user input
                try:
                    user_input = input("\nCommand> ").strip()
                except (EOFError, KeyboardInterrupt):
                    print("\n[!] Interrupted. Exiting.")
                    break

                if not user_input:
                    continue

                # Process commands
                parts = user_input.lower().split()
                cmd = parts[0]

                if cmd == "exit":
                    break

                elif cmd == "help":
                    self.print_help()

                elif cmd == "status":
                    status = "CONNECTED" if self.connected else "DISCONNECTED"
                    auto_status = "RUNNING" if self.auto_send else "STOPPED"
                    print(f"Connection: {status}")
                    print(f"Auto-send: {auto_status}")
                    print(f"Interval: {self.send_interval}s")
                    print(f"Next command index: {self.current_command_index + 1}/{len(self.commands)}")

                elif cmd == "send" and len(parts) > 1:
                    # Join the rest as the command (preserve original case for the command itself)
                    command_to_send = user_input[5:].strip()
                    self.send_command(command_to_send)

                elif cmd == "auto":
                    if len(parts) > 1:
                        subcmd = parts[1]
                        if subcmd == "start":
                            if len(parts) > 2:
                                try:
                                    interval = float(parts[2])
                                    self.start_auto_send(interval)
                                except ValueError:
                                    print("[!] Invalid interval. Using default.")
                                    self.start_auto_send()
                            else:
                                self.start_auto_send()
                        elif subcmd == "stop":
                            self.stop_auto_send()
                        else:
                            print("[!] Invalid auto command. Use 'auto start' or 'auto stop'")
                    else:
                        print("[!] Specify 'start' or 'stop'")

                elif cmd == "interval" and len(parts) > 1:
                    try:
                        self.set_interval(parts[1])
                    except ValueError:
                        print("[!] Invalid interval value")

                elif cmd == "list":
                    self.print_command_list()

                elif cmd == "next":
                    if self.current_command_index >= len(self.commands):
                        self.current_command_index = 0

                    command = self.commands[self.current_command_index]
                    self.send_command(command)
                    self.current_command_index += 1

                else:
                    print("[!] Unknown command. Type 'help' for available commands.")

        finally:
            self.disconnect()
            print("\n[+] Goodbye!")

def main():
    # Configuration - change these as needed
    HOST = "localhost"  # Change to your target IP
    PORT = 8389         # Change to your target port

    sender = TrackerCommandSender(HOST, PORT)
    sender.run_interactive()

if __name__ == "__main__":
    main()