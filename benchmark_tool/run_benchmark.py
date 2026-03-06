import subprocess
import time
import platform
import os
import sys
import shutil

DATA_REPO_URL = "https://github.com/michalbcz/cetnost-jmen-a-prijmeni"
DATA_DIR = "data_source"
ZIP_FILE = "cetnost-prijmeni-obec.zip"
XLS_FILE_ORIGINAL = "četnost-příjmení-obec.xls"
XLS_FILE_RENAMED = "cetnost-prijmeni.xls"

def get_hardware_info():
    info = {
        "os": platform.system(),
        "os_release": platform.release(),
        "machine": platform.machine(),
        "processor": platform.processor(),
    }

    # Try to get CPU info on Linux
    if platform.system() == "Linux":
        try:
            with open("/proc/cpuinfo", "r") as f:
                for line in f:
                    if "model name" in line:
                        info["cpu_model"] = line.split(":")[1].strip()
                        break
        except Exception:
            info["cpu_model"] = "Unknown"

        try:
            with open("/proc/meminfo", "r") as f:
                for line in f:
                    if "MemTotal" in line:
                        mem_kb = int(line.split()[1])
                        info["ram_gb"] = round(mem_kb / (1024 * 1024), 2)
                        break
        except Exception:
            info["ram_gb"] = "Unknown"

    return info

def run_command(command, description):
    print(f"Running: {description}")
    print(f"Command: {' '.join(command)}")

    start_time = time.time()

    try:
        process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        stdout, stderr = process.communicate()
        end_time = time.time()

        duration = end_time - start_time

        if process.returncode != 0:
            print(f"Error executing command: {stderr}")
            return False, stdout, duration

        return True, stdout, duration

    except Exception as e:
        end_time = time.time()
        print(f"Exception executing command: {e}")
        return False, "", end_time - start_time

def setup_data():
    if not os.path.exists(DATA_DIR):
        print("Cloning data repository...")
        success, out, duration = run_command(["git", "clone", DATA_REPO_URL, DATA_DIR], "Clone data repo")
        if not success:
            print("Failed to clone repository. Exiting.")
            sys.exit(1)

    zip_path = os.path.join(DATA_DIR, ZIP_FILE)
    if not os.path.exists(zip_path):
        print(f"Error: {zip_path} not found in the cloned repository.")
        sys.exit(1)

    xls_path = os.path.join(DATA_DIR, XLS_FILE_RENAMED)
    if not os.path.exists(xls_path):
        print("Unzipping data file...")
        success, out, duration = run_command(["unzip", "-o", zip_path, "-d", DATA_DIR], "Unzip data file")
        if not success:
            print("Failed to unzip data. Exiting.")
            sys.exit(1)

        original_unzipped = os.path.join(DATA_DIR, XLS_FILE_ORIGINAL)
        if os.path.exists(original_unzipped):
            os.rename(original_unzipped, xls_path)
        else:
            print(f"Warning: Expected unzipped file {original_unzipped} not found.")
            # Check if there's any .xls file we can use
            for f in os.listdir(DATA_DIR):
                if f.endswith(".xls"):
                    os.rename(os.path.join(DATA_DIR, f), xls_path)
                    print(f"Renamed {f} to {XLS_FILE_RENAMED}")
                    break

    if not os.path.exists(xls_path):
        print(f"Error: Could not prepare {xls_path} for benchmarking.")
        sys.exit(1)

    return xls_path

def build_jar():
    jar_path = "target/csvmultitool-1.0-SNAPSHOT-shaded.jar"
    if not os.path.exists(jar_path):
        # Fallback to non-shaded if shaded doesn't exist but we built it
        jar_path = "target/csvmultitool-1.0-SNAPSHOT.jar"

    if not os.path.exists(jar_path):
        print("Building Java project...")
        success, out, duration = run_command(["mvn", "clean", "package"], "Maven build")
        if not success:
            print("Failed to build Java project. Exiting.")
            sys.exit(1)

        jar_path = "target/csvmultitool-1.0-SNAPSHOT-shaded.jar"
        if not os.path.exists(jar_path):
            jar_path = "target/csvmultitool-1.0-SNAPSHOT.jar"

    if not os.path.exists(jar_path):
        print("Error: Could not find built JAR file.")
        sys.exit(1)

    return jar_path

def main():
    print("--- Benchmark Setup ---")
    excel_file = setup_data()
    jar_file = build_jar()
    print("Setup complete.\n")

    print("--- Hardware Info ---")
    hw_info = get_hardware_info()
    for key, value in hw_info.items():
        print(f"{key}: {value}")
    print("---------------------\n")

    print("Running Sheet Listing Benchmarks...")

    # Run Python csvkit benchmark (Sheet Listing)
    csvkit_cmd_n = ["in2csv", "-n", excel_file]
    success_csvkit_n, output_csvkit_n, time_csvkit_n = run_command(csvkit_cmd_n, "Python (csvkit) sheet listing")

    if success_csvkit_n:
        print(f"csvkit time: {time_csvkit_n:.3f} seconds\n")
    else:
        print("Python (csvkit) benchmark failed.\n")

    # Run Java application benchmark (Sheet Listing)
    java_cmd_n = ["java", "-jar", jar_file, "in2csv", "-n", excel_file]
    success_java_n, output_java_n, time_java_n = run_command(java_cmd_n, "Java (csvmultitool) sheet listing")

    if success_java_n:
        print(f"Java time: {time_java_n:.3f} seconds\n")
    else:
        print("Java benchmark failed.\n")


    print("Running Full Data Extraction Benchmarks...")

    # For full extraction, we extract a specific sheet to a temp file
    # We will use the first sheet which is simply passing no -s arguments
    # to avoid unicode issues in shell on the CI.
    csvkit_out = "csvkit_output.csv"
    java_out = "java_output.csv"

    # Python full extraction
    csvkit_cmd_extract = f"in2csv {excel_file} > {csvkit_out}"
    print(f"Running: Python (csvkit) full extraction")
    start_time = time.time()
    os.system(csvkit_cmd_extract)
    time_csvkit_extract = time.time() - start_time
    print(f"csvkit time: {time_csvkit_extract:.3f} seconds\n")

    # Java full extraction
    java_cmd_extract = f"java -jar {jar_file} in2csv {excel_file} > {java_out}"
    print(f"Running: Java (csvmultitool) full extraction")
    start_time = time.time()
    os.system(java_cmd_extract)
    time_java_extract = time.time() - start_time
    print(f"Java time: {time_java_extract:.3f} seconds\n")


    print("--- Summary: Sheet Listing ---")
    if success_csvkit_n and success_java_n:
        print(f"Python (csvkit): {time_csvkit_n:.3f}s")
        print(f"Java (csvmultitool): {time_java_n:.3f}s")
        speedup_n = time_csvkit_n / time_java_n if time_java_n > 0 else 0
        print(f"Java is {speedup_n:.2f}x faster")
    print("------------------------------\n")

    print("--- Summary: Full Extraction ---")
    print(f"Python (csvkit): {time_csvkit_extract:.3f}s")
    print(f"Java (csvmultitool): {time_java_extract:.3f}s")
    speedup_ex = time_csvkit_extract / time_java_extract if time_java_extract > 0 else 0
    print(f"Java is {speedup_ex:.2f}x faster")
    print("--------------------------------\n")

    # Cleanup temp files
    if os.path.exists(csvkit_out):
        os.remove(csvkit_out)
    if os.path.exists(java_out):
        os.remove(java_out)

if __name__ == "__main__":
    main()
