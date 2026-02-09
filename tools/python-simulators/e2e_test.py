import requests
import json
import time
import threading
import sys
import subprocess
from typing import Dict, List, Optional, Any
from datetime import datetime, timedelta
import random

class TrackingSystemE2ETest:
    def __init__(self, base_url: str = "http://localhost:8080"):
        self.base_url = base_url
        self.session = requests.Session()
        self.test_data = {
            "vehicles": [],
            "trackers": [],
            "geofences": [],
            "alert_rules": [],
            "alerts": [],
            "trips": []
        }

    def setup_headers(self):
        """Setup request headers"""
        headers = {
            "Content-Type": "application/json",
            "Accept": "application/json"
        }
        # Add authentication if needed
        # headers["Authorization"] = "Bearer YOUR_TOKEN"
        return headers

    def log_request(self, method: str, endpoint: str, data: dict = None):
        """Log API requests"""
        print(f"\n{'='*60}")
        print(f"[{method}] {self.base_url}{endpoint}")
        if data:
            print(f"Request Body: {json.dumps(data, indent=2)}")
        print(f"{'='*60}")

    def log_response(self, response):
        """Log API responses"""
        try:
            response_json = response.json()
            print(f"Response ({response.status_code}): {json.dumps(response_json, indent=2)}")
        except:
            print(f"Response ({response.status_code}): {response.text}")
        return response

    def api_request(self, method: str, endpoint: str, data: dict = None, params: dict = None):
        """Make API request with logging"""
        self.log_request(method, endpoint, data)

        url = f"{self.base_url}{endpoint}"
        headers = self.setup_headers()

        try:
            if method.upper() == "GET":
                response = self.session.get(url, headers=headers, params=params)
            elif method.upper() == "POST":
                response = self.session.post(url, headers=headers, json=data)
            elif method.upper() == "PUT":
                response = self.session.put(url, headers=headers, json=data)
            elif method.upper() == "DELETE":
                response = self.session.delete(url, headers=headers)
            elif method.upper() == "PATCH":
                response = self.session.patch(url, headers=headers)
            else:
                raise ValueError(f"Unsupported method: {method}")

            return self.log_response(response)

        except Exception as e:
            print(f"Request failed: {e}")
            raise

    def create_vehicle(self, vehicle_id: str, device_id: str = None):
        """Create a test vehicle"""
        if not device_id:
            device_id = f"DEV{vehicle_id}"

        vehicle_data = {
            "vehicleId": vehicle_id,
            "deviceId": device_id,
            "model": "Toyota Hilux",
            "licensePlate": f"ABC-{random.randint(100, 999)}",
            "vin": f"VIN{random.randint(1000000, 9999999)}",
            "fuelLevel": 75.5,
            "odometerKm": 12345.6
        }

        response = self.api_request("POST", "/api/v1/vehicles", vehicle_data)

        if response.status_code in [201, 200]:
            self.test_data["vehicles"].append(vehicle_id)
            print(f"[✓] Vehicle created: {vehicle_id}")
            return vehicle_id
        else:
            print(f"[✗] Failed to create vehicle {vehicle_id}")
            return None

    def create_tracker(self, tracker_id: str, device_id: str, vehicle_id: str = None):
        """Create a test tracker"""
        tracker_data = {
            "trackerId": tracker_id,
            "deviceId": device_id,
            "model": "Autoseeker AS-101",
            "protocol": "AUTOSEEKER",
            "firmwareVersion": "V2.1.8",
            "simNumber": f"+123456789{random.randint(10, 99)}",
            "vehicleId": vehicle_id if vehicle_id else ""
        }

        response = self.api_request("POST", "/api/v1/trackers", tracker_data)

        if response.status_code in [201, 200]:
            self.test_data["trackers"].append({
                "trackerId": tracker_id,
                "deviceId": device_id,
                "vehicleId": vehicle_id
            })
            print(f"[✓] Tracker created: {tracker_id} (Device: {device_id})")
            return tracker_id
        else:
            print(f"[✗] Failed to create tracker {tracker_id}")
            return None

    def assign_tracker_to_vehicle(self, tracker_id: str, vehicle_id: str):
        """Assign tracker to vehicle"""
        # First get tracker to update
        response = self.api_request("GET", f"/api/v1/trackers/{tracker_id}")

        if response.status_code == 200:
            tracker_data = response.json()
            tracker_data["vehicleId"] = vehicle_id

            update_response = self.api_request("PUT", f"/api/v1/trackers/{tracker_id}", tracker_data)

            if update_response.status_code == 200:
                print(f"[✓] Tracker {tracker_id} assigned to vehicle {vehicle_id}")
                return True

        print(f"[✗] Failed to assign tracker {tracker_id} to vehicle {vehicle_id}")
        return False

    def create_geofence(self, vehicle_id: str, name: str = "Test Geofence"):
        """Create a circular geofence"""
        geofence_data = {
            "vehicleId": vehicle_id,
            "name": name,
            "shapeType": "CIRCLE",
            "centerLatitude": 22.582122,
            "centerLongitude": 113.906633,
            "radiusMeters": 500,
            "active": True,
            "createdBy": "test-script"
        }

        response = self.api_request("POST", "/alerts/geofences", geofence_data)

        if response.status_code == 200:
            geofence = response.json()
            geofence_id = geofence.get("geofenceId")
            self.test_data["geofences"].append(geofence_id)
            print(f"[✓] Geofence created: {geofence_id}")
            return geofence_id
        else:
            print(f"[✗] Failed to create geofence")
            return None

    def create_overspeed_alert_rule(self, vehicle_ids: List[str], speed_limit: float = 80.0):
        """Create overspeed alert rule"""
        rule_data = {
            "ruleKey": f"overspeed_test_{int(time.time())}",
            "ruleName": f"Overspeed Test Rule",
            "speedLimit": speed_limit,
            "buffer": 5.0,
            "vehicleIds": vehicle_ids,
            "priority": 1,
            "enabled": True
        }

        response = self.api_request("POST", "/alerts/rules/templates/overspeed", rule_data)

        if response.status_code == 200:
            rule = response.json()
            rule_key = rule.get("ruleKey")
            self.test_data["alert_rules"].append(rule_key)
            print(f"[✓] Overspeed rule created: {rule_key}")
            return rule_key
        else:
            print(f"[✗] Failed to create overspeed rule")
            return None

    def create_geofence_alert_rule(self, vehicle_ids: List[str], geofence_id: str):
        """Create geofence alert rule"""
        rule_data = {
            "ruleKey": f"geofence_test_{int(time.time())}",
            "ruleName": f"Geofence Test Rule",
            "geofenceId": geofence_id,
            "action": "BOTH",  # Trigger on both entry and exit
            "vehicleIds": vehicle_ids,
            "priority": 2,
            "enabled": True
        }

        response = self.api_request("POST", "/alerts/rules/templates/geofence", rule_data)

        if response.status_code == 200:
            rule = response.json()
            rule_key = rule.get("ruleKey")
            self.test_data["alert_rules"].append(rule_key)
            print(f"[✓] Geofence rule created: {rule_key}")
            return rule_key
        else:
            print(f"[✗] Failed to create geofence rule")
            return None

    def check_vehicle_status(self, vehicle_id: str):
        """Check vehicle current status"""
        response = self.api_request("GET", f"/api/v1/vehicles/{vehicle_id}")

        if response.status_code == 200:
            vehicle = response.json()
            print(f"\n[Vehicle Status]")
            print(f"  ID: {vehicle.get('vehicleId')}")
            print(f"  Engine State: {vehicle.get('engineState')}")
            print(f"  ACC Status: {vehicle.get('accStatus')}")
            print(f"  Last Telemetry: {vehicle.get('lastTelemetryTime')}")

            if vehicle.get('currentLocation'):
                loc = vehicle['currentLocation']
                print(f"  Current Location: {loc.get('latitude')}, {loc.get('longitude')}")
                print(f"  Speed: {loc.get('speedKmh')} km/h")

            return vehicle
        return None

    def check_active_trips(self, vehicle_id: str):
        """Check for active trips"""
        response = self.api_request("GET", f"/api/v1/trips/vehicle/{vehicle_id}/active")

        if response.status_code == 200:
            trip = response.json()
            if trip:
                print(f"\n[Active Trip Found]")
                print(f"  Trip ID: {trip.get('tripId')}")
                print(f"  Start Time: {trip.get('startTime')}")
                print(f"  Distance: {trip.get('totalDistanceKm')} km")
                self.test_data["trips"].append(trip.get('tripId'))
            else:
                print("\n[No Active Trip]")
            return trip
        return None

    def check_alerts(self, vehicle_id: str, limit: int = 10):
        """Check recent alerts for vehicle"""
        params = {
            "arg5": vehicle_id,  # vehicleId filter
            "arg0": 0,  # page
            "arg1": limit  # size
        }

        response = self.api_request("GET", "/alerts", params=params)

        if response.status_code == 200:
            alerts_page = response.json()
            alerts = alerts_page.get('content', [])

            if alerts:
                print(f"\n[Recent Alerts for {vehicle_id}]:")
                for alert in alerts[:5]:  # Show first 5
                    print(f"  - {alert.get('alertType')}: {alert.get('message')}")
                    print(f"    Severity: {alert.get('severity')}, Time: {alert.get('triggeredAt')}")
            else:
                print(f"\n[No alerts found for {vehicle_id}]")

            return alerts
        return []

    def get_alert_statistics(self):
        """Get alert statistics"""
        response = self.api_request("GET", "/alerts/stats/unacknowledged")

        if response.status_code == 200:
            stats = response.json()
            print(f"\n[Alert Statistics]")
            for severity, count in stats.items():
                print(f"  {severity}: {count}")
            return stats
        return {}

    def send_test_location(self, tracker_id: str, latitude: float, longitude: float, speed: float = 0.0):
        """Manually send a location via API (alternative to device simulator)"""
        params = {
            "arg0": tracker_id,  # trackerId
            "arg1": latitude,
            "arg2": longitude,
            "arg3": speed,
            "arg4": datetime.now().isoformat()
        }

        response = self.api_request("POST", "/api/v1/locations/record", params=params)

        if response.status_code == 200:
            print(f"[✓] Location sent: {latitude}, {longitude} @ {speed} km/h")
            return True
        else:
            print(f"[✗] Failed to send location")
            return False

    def run_device_simulator(self, device_id: str, tracker_id: str, host: str = "localhost", port: int = 8888):
        """Run your existing device simulator in a subprocess"""
        print(f"\n[Starting Device Simulator]")
        print(f"  Device ID: {device_id}")
        print(f"  Tracker ID: {tracker_id}")
        print(f"  Server: {host}:{port}")

        # Run your existing simulator script
        simulator_path = "./tools/python-simulators/autoseeeker-device/autoseeker_device_simulator.py"

        try:
            # Modify the simulator to use our device_id
            process = subprocess.Popen([
                "python", simulator_path,
                "--host", host,
                "--port", str(port),
                "--device-id", device_id
            ], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)

            # Give it time to start
            time.sleep(3)

            print(f"[✓] Device simulator started (PID: {process.pid})")
            return process

        except Exception as e:
            print(f"[✗] Failed to start device simulator: {e}")
            return None

    def run_simulation_scenario(self, vehicle_id: str, tracker_id: str, device_id: str):
        """Run a test simulation scenario"""
        print(f"\n{'#'*70}")
        print(f"STARTING SIMULATION SCENARIO")
        print(f"Vehicle: {vehicle_id}, Tracker: {tracker_id}, Device: {device_id}")
        print(f"{'#'*70}")

        # Start device simulator
        simulator_process = self.run_device_simulator(device_id, tracker_id)

        if not simulator_process:
            print("[!] Cannot continue without device simulator")
            return False

        try:
            # Wait for initial data
            print("\n[Waiting for initial device data...]")
            time.sleep(15)

            # Check vehicle status after simulator sends data
            print("\n[Checking initial status...]")
            self.check_vehicle_status(vehicle_id)

            # Send some test locations manually to trigger alerts
            print("\n[Sending test locations to trigger alerts...]")

            # Location 1: Inside geofence, normal speed
            self.send_test_location(tracker_id, 22.582122, 113.906633, 50.0)
            time.sleep(5)

            # Location 2: Outside geofence, high speed (should trigger overspeed)
            self.send_test_location(tracker_id, 22.600000, 114.000000, 90.0)
            time.sleep(5)

            # Location 3: Back inside, normal speed
            self.send_test_location(tracker_id, 22.582122, 113.906633, 60.0)
            time.sleep(10)

            # Check for active trips
            print("\n[Checking for trips...]")
            self.check_active_trips(vehicle_id)

            # Check for alerts
            print("\n[Checking for alerts...]")
            self.check_alerts(vehicle_id)

            # Get statistics
            self.get_alert_statistics()

            # Monitor for a bit more
            print("\n[Monitoring for additional data...]")
            for i in range(3):
                time.sleep(10)
                print(f"\n[Monitor check {i+1}/3]")
                self.check_vehicle_status(vehicle_id)
                alerts = self.check_alerts(vehicle_id, 5)
                if alerts:
                    print(f"  New alerts detected: {len(alerts)}")

            return True

        except KeyboardInterrupt:
            print("\n[!] Simulation interrupted")
        finally:
            # Stop simulator
            if simulator_process:
                print("\n[Stopping device simulator...]")
                simulator_process.terminate()
                simulator_process.wait()

        return True

    def cleanup_test_data(self):
        """Clean up test data (optional)"""
        print(f"\n{'#'*70}")
        print("CLEANUP TEST DATA")
        print(f"{'#'*70}")

        # Clean up alert rules
        for rule_key in self.test_data["alert_rules"]:
            try:
                self.api_request("DELETE", f"/alerts/rules/{rule_key}")
                print(f"[✓] Deleted alert rule: {rule_key}")
            except:
                print(f"[✗] Failed to delete alert rule: {rule_key}")

        # Clean up geofences
        for geofence_id in self.test_data["geofences"]:
            try:
                self.api_request("DELETE", f"/alerts/geofences/{geofence_id}")
                print(f"[✓] Deleted geofence: {geofence_id}")
            except:
                print(f"[✗] Failed to delete geofence: {geofence_id}")

        # Clean up trackers
        for tracker in self.test_data["trackers"]:
            tracker_id = tracker["trackerId"]
            try:
                self.api_request("DELETE", f"/api/v1/trackers/{tracker_id}")
                print(f"[✓] Deleted tracker: {tracker_id}")
            except:
                print(f"[✗] Failed to delete tracker: {tracker_id}")

        # Clean up vehicles
        for vehicle_id in self.test_data["vehicles"]:
            try:
                self.api_request("DELETE", f"/api/v1/vehicles/{vehicle_id}")
                print(f"[✓] Deleted vehicle: {vehicle_id}")
            except:
                print(f"[✗] Failed to delete vehicle: {vehicle_id}")

        print("\n[✓] Cleanup completed")

    def run_full_test(self):
        """Run complete end-to-end test"""
        print(f"\n{'*'*80}")
        print("TRACKING SYSTEM END-TO-END TEST")
        print(f"{'*'*80}")

        try:
            # Step 1: Create test vehicle
            print("\n[STEP 1: Creating test vehicle...]")
            vehicle_id = "TEST_VEHICLE_001"
            device_id = "8168000005"  # Match your simulator device ID

            vehicle_id_created = self.create_vehicle(vehicle_id, device_id)
            if not vehicle_id_created:
                print("[!] Failed to create vehicle. Aborting test.")
                return False

            # Step 2: Create test tracker
            print("\n[STEP 2: Creating test tracker...]")
            tracker_id = "TEST_TRACKER_001"

            tracker_id_created = self.create_tracker(tracker_id, device_id, vehicle_id)
            if not tracker_id_created:
                print("[!] Failed to create tracker. Aborting test.")
                return False

            # Step 3: Verify assignment (should already be assigned during creation)
            print("\n[STEP 3: Verifying tracker-vehicle assignment...]")
            self.check_vehicle_status(vehicle_id)

            # Step 4: Create geofence
            print("\n[STEP 4: Creating geofence...]")
            geofence_id = self.create_geofence(vehicle_id, "Test Warehouse Area")

            # Step 5: Create alert rules
            print("\n[STEP 5: Creating alert rules...]")
            overspeed_rule = self.create_overspeed_alert_rule([vehicle_id], 80.0)
            if geofence_id:
                geofence_rule = self.create_geofence_alert_rule([vehicle_id], geofence_id)

            # Step 6: Run simulation
            print("\n[STEP 6: Running device simulation...]")
            success = self.run_simulation_scenario(vehicle_id, tracker_id, device_id)

            if not success:
                print("[!] Simulation failed")
                return False

            # Step 7: Final verification
            print("\n[STEP 7: Final verification...]")
            time.sleep(5)

            final_vehicle = self.check_vehicle_status(vehicle_id)
            final_alerts = self.check_alerts(vehicle_id, 20)
            final_trips = self.check_active_trips(vehicle_id)

            print(f"\n{'*'*80}")
            print("TEST SUMMARY")
            print(f"{'*'*80}")
            print(f"Vehicle Created: {'✓' if vehicle_id_created else '✗'}")
            print(f"Tracker Created: {'✓' if tracker_id_created else '✗'}")
            print(f"Geofence Created: {'✓' if geofence_id else '✗'}")
            print(f"Alert Rules Created: {'✓' if overspeed_rule else '✗'}")
            print(f"Alerts Triggered: {len(final_alerts)}")
            print(f"Active Trips: {'Yes' if final_trips else 'No'}")
            print(f"Simulation Success: {'✓' if success else '✗'}")

            # Ask about cleanup
            cleanup = input("\nDo you want to cleanup test data? (y/n): ").lower()
            if cleanup == 'y':
                self.cleanup_test_data()
            else:
                print("\nTest data preserved for manual inspection.")

            print(f"\n[✓] End-to-end test completed successfully!")
            return True

        except Exception as e:
            print(f"\n[✗] Test failed with error: {e}")
            import traceback
            traceback.print_exc()
            return False

def main():
    """Main function to run the E2E test"""
    # You can customize the base URL if needed
    base_url = "http://localhost:8080"

    # Check if server is running
    print("Checking if server is available...")
    try:
        response = requests.get(f"{base_url}/actuator/health", timeout=5)
        if response.status_code == 200:
            print(f"[✓] Server is running at {base_url}")
        else:
            print(f"[✗] Server responded with status {response.status_code}")
            print("Please start the Spring Boot application first.")
            return
    except requests.exceptions.ConnectionError:
        print(f"[✗] Cannot connect to {base_url}")
        print("Please make sure the Spring Boot application is running.")
        return

    # Create and run test
    tester = TrackingSystemE2ETest(base_url)

    # Run full test
    success = tester.run_full_test()

    if success:
        print("\n" + "="*80)
        print("🎉 END-TO-END TEST COMPLETED SUCCESSFULLY!")
        print("="*80)
        print("\nYou can now manually test using:")
        print(f"1. Swagger UI: {base_url}/swagger-ui.html")
        print(f"2. H2 Console: {base_url}/h2-console (JDBC URL: jdbc:h2:mem:trackingdb)")
        print(f"3. Direct API calls to {base_url}/api/v1/...")
    else:
        print("\n" + "="*80)
        print("❌ END-TO-END TEST FAILED")
        print("="*80)
        sys.exit(1)

if __name__ == "__main__":
    main()