import requests
import json
import re
from bs4 import BeautifulSoup

# Set your SonarQube server details
SONARQUBE_URL = "http://localhost:9000"
USERNAME = "admin"
PASSWORD = "134679_Samya"
PROJECT_KEY = "OpenRefine"

# Define the search endpoint to get the list of security hotspots
search_endpoint = f"{SONARQUBE_URL}/api/hotspots/search"
search_params = {
    "projectKey": PROJECT_KEY
}

# Step 1: Get security hotspots
response = requests.get(search_endpoint, auth=(USERNAME, PASSWORD), params=search_params)
if response.status_code == 200:
    hotspots_data = response.json()
    hotspots = hotspots_data.get("hotspots", [])

    detailed_hotspots = []

    # Step 2: Iterate through each hotspot to get detailed information
    for hotspot in hotspots:
        hotspot_key = hotspot.get("key")
        if hotspot_key:
            # Use /api/hotspots/show to get detailed information about each hotspot
            show_endpoint = f"{SONARQUBE_URL}/api/hotspots/show"
            show_params = {
                "hotspot": hotspot_key
            }
            detailed_response = requests.get(show_endpoint, auth=(USERNAME, PASSWORD), params=show_params)
            if detailed_response.status_code == 200:
                detailed_data = detailed_response.json()
                # Extract required attributes and rename them
                severity = detailed_data.get("rule", {}).get("vulnerabilityProbability", "").upper()

                # Only include items with severity "HIGH" or "MEDIUM"
                if severity in ["HIGH", "MEDIUM", "LOW"]:
                    project_name = detailed_data.get("project", {}).get("name")
                    file_name = detailed_data.get("component", {}).get("name")
                    file_path = detailed_data.get("component", {}).get("path")
                    security_category = detailed_data.get("rule", {}).get("securityCategory")
                    description = detailed_data.get("rule", {}).get("name")
                    start_line = detailed_data.get("textRange", {}).get("startLine")
                    end_line = detailed_data.get("textRange", {}).get("endLine")
                    location = f"{start_line} - {end_line}"
                    fix_recommendations_html = detailed_data.get("rule", {}).get("fixRecommendations", "")

                    # Remove HTML tags from fixRecommendations and format text better
                    soup = BeautifulSoup(fix_recommendations_html, "html.parser")
                    best_practices = soup.get_text(separator="\n").strip()
                    best_practices = re.sub(r'\n{2,}', '\n',
                                            best_practices)  # Replace multiple newlines with a single newline
                    best_practices_lines = best_practices.split('\n')
                    best_practices = "\n".join([line.strip() for line in best_practices_lines if line.strip()])

                    # Append detailed data to the list
                    detailed_hotspots.append({
                        "projectName": project_name,
                        "fileName": file_name,
                        "filePath": file_path,
                        "Security Category": security_category,
                        "Description": description,
                        "Location": location,
                        "Severity": severity,
                        "Best Practices": best_practices
                    })


    # Save the detailed data to a JSON file
    with open("OpenRefine_security_hotspots.json", "w") as f:
        json.dump(detailed_hotspots, f, indent=4)
    print("Detailed security hotspots exported successfully to detailed_security_hotspots.json")
else:
    print(f"Failed to retrieve hotspots: {response.status_code} - {response.text}")