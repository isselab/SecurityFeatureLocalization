import subprocess
import os
import tempfile
import json
import time

def clone_repository(repo_link, retries=3):
    # Create a temporary directory to clone the repository
    temp_dir = tempfile.mkdtemp()
    for attempt in range(retries):
        try:
            subprocess.run(["git", "clone", repo_link, temp_dir], check=True)
            return temp_dir
        except subprocess.CalledProcessError as e:
            print(f"Attempt {attempt + 1} failed: {e}")
            if attempt < retries - 1:
                time.sleep(5)  # Wait before retrying
            else:
                raise RuntimeError("Failed to clone the repository after multiple attempts.")

def scan_with_semgrep_docker(directory):
    try:
        # Run Semgrep using Docker and scan the given directory for .java files only
        result = subprocess.run([
            "docker", "run", "--rm", "-v", f"{directory}:/src", "returntocorp/semgrep",
            "semgrep", "--config=p/ci", "/src", "--include=*.java", "--json"
        ], capture_output=True, text=True, encoding='utf-8', check=True)
        return json.loads(result.stdout)
    except subprocess.CalledProcessError as e:
        print(f"Error running Semgrep with Docker: {e.stderr}")
        raise

def filter_scan_results(scan_results):
    filtered_results = []
    for result in scan_results.get("results", []):
        metadata = result.get("extra", {}).get("metadata", {})
        filtered_result = {
            "File Path": result.get("path"),
            "Location": f"line {result.get('start', {}).get('line')} to line {result.get('end', {}).get('line')}",
            "Code": result.get("extra", {}).get("lines"),
            "Description": result.get("extra", {}).get("message"),
            "Severity": metadata.get("impact"),
            "Category": metadata.get("category"),
            "Technology": metadata.get("technology"),
            "CWE": metadata.get("cwe"),
            "References": metadata.get("references")
        }
        filtered_results.append(filtered_result)
    return filtered_results

def write_scan_results_to_json(scan_results, output_file):
    # Save the filtered scan results to a JSON file
    with open(output_file, "w") as json_file:
        json.dump(scan_results, json_file, indent=4)

if __name__ == "__main__":
    repo_link = input("Enter the Git repository link: ")
    cloned_repo_dir = None
    try:
        # Clone the repository
        cloned_repo_dir = clone_repository(repo_link)

        # Scan the repository with Semgrep using Docker
        scan_results = scan_with_semgrep_docker(cloned_repo_dir)

        # Filter the scan results
        filtered_results = filter_scan_results(scan_results)

        # Write the filtered scan results to a JSON file
        output_file = "semgrep_Eclipse_tradista.json"
        write_scan_results_to_json(filtered_results, output_file)

        print(f"Scan results saved to {output_file}")
    except RuntimeError as e:
        print(f"Error: {e}")
    finally:
        # Clean up the cloned repository if it exists
        if cloned_repo_dir and os.path.exists(cloned_repo_dir):
            subprocess.run(["rmdir", "/s", "/q", cloned_repo_dir], shell=True, check=True)
