import os
import json
import re
import subprocess
import tempfile
import time
from collections import Counter, defaultdict


def clone_repository(repo_link):
    """Clone the repository into a 'repos' directory with the project name."""
    # Extract project name from the repo URL
    project_name = os.path.basename(repo_link).replace(".git", "")
    repos_dir = os.path.join(os.getcwd(), "repos")
    project_dir = os.path.join(repos_dir, project_name)
    # Create the 'repos' directory if it doesn't exist
    os.makedirs(repos_dir, exist_ok=True)
    if os.path.exists(project_dir):
        print(f"Repository '{project_name}' already exists in 'repos'.")
    else:
        try:
            subprocess.run(["git", "clone", repo_link, project_dir], check=True)
            print(f"Cloned '{project_name}' into 'repos'.")
        except subprocess.CalledProcessError as e:
            raise RuntimeError(f"Failed to clone repository: {e}")
    return project_dir, project_name


def flatten_keywords(keyword_dict):
    """Flatten the nested keyword dictionary into a list with categories."""
    flattened = []
    for category, subcategories in keyword_dict.items():
        if isinstance(subcategories, dict):
            for subcategory, keywords in subcategories.items():
                for keyword in keywords:
                    flattened.append((category, subcategory, keyword))
        elif isinstance(subcategories, list):  # For keywords directly under a category
            for keyword in subcategories:
                flattened.append((category, "Miscellaneous", keyword))
    return flattened


def process_feature_annotations(features_file, repo_dir, library_features):
    with open(features_file, "r") as file:
        data = json.load(file)

    for source in data.get('sources', []):
        for feature in source.get('files', []):
            file_path = os.path.join(repo_dir, feature.get('path', ''))
            if not os.path.exists(file_path) or not feature.get('apiCalls'):
                continue

            with open(file_path, "r", encoding="utf-8", errors="ignore") as f:
                lines = f.readlines()

            # Collect annotations per line
            line_annotations = defaultdict(set)

            for api_call in feature['apiCalls']:
                line_index = api_call.get('line', 0)
                feature_names = api_call.get('features', [])
                method_name = api_call.get('api', '').split('.')[-1]

                if feature_names and line_index < len(lines):
                    for feature_name in feature_names:
                        tag = f"{feature_name}_{method_name}_L"
                        line_annotations[line_index].add(tag)
                        library_features.add(tag)

            # Apply annotations to lines
            for line_index, tags in line_annotations.items():
                annotation = ""
                if len(tags) == 1:
                    tag = next(iter(tags))
                    annotation = f"// &line[{tag}]"
                else:
                    tags_str = ", ".join(sorted(tags))
                    annotation = f"// &line[{tags_str}]"

                if annotation not in lines[line_index]:
                    lines[line_index] = lines[line_index].rstrip() + f" {annotation}\n"

            with open(file_path, "w", encoding="utf-8") as f:
                f.writelines(lines)


def search_keywords_in_file(file_path, flattened_keywords, repo_dir, pos_counter, pos_list, keyword_counter,
                            hans_exclusion_counter):
    """Search for keywords in a given file and return matches, excluding comments, test-related code, and HAnS-annotated code."""
    matches = {}
    short_path = os.path.relpath(file_path, repo_dir)
    if "src\\" in short_path:
        short_path = short_path[short_path.index("src\\"):]
    updated_lines = []  # Store updated lines with added comments
    added_positions = set()  # Track lines where a position has been added
    hans_lines_seen = set()  # Store unique Hans line patterns

    in_multiline_comment = False
    in_testing_context = False
    in_hans_annotated_block = False  # Track if inside an annotated block

    hans_begin_pattern = re.compile(r"//\s*&begin\[(.*?)\]")  # Match // &begin[FeatureName]
    hans_end_pattern = re.compile(r"//\s*&end\[(.*?)\]")  # Match // &end[FeatureName]
    hans_line_pattern = re.compile(r"&line\[[^\]]+\]")  # Match inline annotations

    string_literal_pattern = re.compile(r'".*?"')  # Match anything inside "..."
    single_line_comment_pattern = re.compile(r"//.*")  # Match anything after //
    multi_line_comment_start_pattern = re.compile(r"/\*")  # Match /* (start of multi-line comment)
    multi_line_comment_end_pattern = re.compile(r"\*/")  # Match */ (end of multi-line comment)

    with open(file_path, "r", encoding="utf-8", errors="ignore") as file:
        for line_number, line in enumerate(file, start=1):
            stripped_line = line.strip()

            # Skip import statements
            if "import" in stripped_line:
                updated_lines.append(line)
                continue

            # Skip lines that are individually annotated with HAnS
            if hans_line_pattern.search(stripped_line):
                hans_matches = hans_line_pattern.findall(stripped_line)
                for match in hans_matches:
                    if match not in hans_lines_seen:  # Count only if not seen before
                        hans_exclusion_counter[0] += 1
                        hans_lines_seen.add(match)
                updated_lines.append(line)
                continue

            # Handle multi-line comments
            if multi_line_comment_start_pattern.search(stripped_line):
                in_multiline_comment = True
            if in_multiline_comment:
                updated_lines.append(line)
                if multi_line_comment_end_pattern.search(stripped_line):
                    in_multiline_comment = False
                continue

            # Detect if inside a test class or function
            if re.search(r"\bclass\b.*Test|@Test\b", stripped_line):
                in_testing_context = True
            if in_testing_context and re.search(r"^\}", stripped_line):
                # Exit test context when a closing brace is found
                in_testing_context = False

            # Detect and handle HAnS-annotated blocks
            if hans_begin_pattern.search(stripped_line):
                in_hans_annotated_block = True
            if hans_end_pattern.search(stripped_line):
                in_hans_annotated_block = False
                updated_lines.append(line)  # Preserve the closing annotation
                continue

            # Skip lines inside test contexts or inside a HAnS-annotated block
            if in_testing_context or in_hans_annotated_block:
                updated_lines.append(line)
                continue

            # Skip single-line comments
            if single_line_comment_pattern.search(stripped_line):
                updated_lines.append(line)
                continue

            # Remove all string literals from the line before searching for keywords
            cleaned_line = string_literal_pattern.sub("", stripped_line)

            # Search only non-comment, non-test, non-HAnS-annotated lines
            keywords_found = {}
            for category, subcategory, keyword in flattened_keywords:
                if re.search(rf"\b{re.escape(keyword)}\b", cleaned_line):
                    key = f"{category} : {subcategory}"
                    if key not in keywords_found:
                        keywords_found[key] = []
                    keywords_found[key].append(keyword)

            if keywords_found:
                if line_number not in matches:
                    matches[line_number] = {
                        "Source Code": line.strip(),
                        "Location": f"line {line_number}",
                        "Keywords Found": {}
                    }
                # Merge all found keywords for the same category and subcategory
                for key, keywords in keywords_found.items():
                    if key not in matches[line_number]["Keywords Found"]:
                        matches[line_number]["Keywords Found"][key] = []
                    matches[line_number]["Keywords Found"][key].extend(keywords)
                    for keyword in keywords:
                        # Increment the counter with the correct category, subcategory, and keyword
                        keyword_counter[(key.split(" : ")[0], key.split(" : ")[1], keyword)] += 1

                # Add begin and end comments only once for the line
                if line_number not in added_positions:
                    comment_start = f"// &begin[Pos{pos_counter[0]}]\n"
                    updated_lines.append(comment_start)
                    updated_lines.append(line)  # Add the line with the match
                    comment_end = f"// &end[Pos{pos_counter[0]}]\n"
                    updated_lines.append(comment_end)
                    pos_list.append(f"Pos{pos_counter[0]}")
                    pos_counter[0] += 1
                    added_positions.add(line_number)
            else:
                updated_lines.append(line)

    # Consolidate the "Keywords Found" for each line
    for match in matches.values():
        consolidated_keywords = []
        for key, keywords in match["Keywords Found"].items():
            unique_keywords = set(keywords)
            consolidated_keywords.append(f"{key} : [{', '.join(unique_keywords)}]")
        match["Keywords Found"] = ", ".join(consolidated_keywords)

    # Save updated file with comments
    with open(file_path, "w", encoding="utf-8") as file:
        file.writelines(updated_lines)

    return os.path.basename(file_path), short_path, list(matches.values())


def search_codebase(temp_dir, flattened_keywords, hans_exclusion_counter):
    """Search the entire codebase for security keywords and group matches by file."""
    results = []
    pos_counter = [1]  # Counter for the global position numbers
    pos_list = []  # List to store all PosX values
    keyword_counter = Counter()  # Counter to track keyword occurrences, including category and subcategory
    for root, _, files in os.walk(temp_dir):
        for file in files:
            if file.endswith(".java") and "test" not in file.lower():
                file_path = os.path.join(root, file)
                file_name, short_path, matches = search_keywords_in_file(
                    file_path, flattened_keywords, temp_dir, pos_counter, pos_list, keyword_counter,
                    hans_exclusion_counter
                )
                if matches:
                    results.append({
                        "File Name": file_name,
                        "File Path": short_path,
                        "Matches": matches
                    })
    return results, pos_list, keyword_counter


def save_results_to_json(repo_name, results):
    """Save the results to a JSON file."""
    output_file = f"{repo_name}_PotentialFeatLocations.json"
    with open(output_file, "w") as file:
        json.dump(results, file, indent=4)
    print(f"Results saved to {output_file}")


def create_feature_model_file(repo_dir, custom_features, library_features):
    feature_model_path = os.path.join(repo_dir, ".feature-model")
    with open(feature_model_path, "w") as file:
        if library_features:
            file.write("Security_Features_Library_Tool\n")
            for feat in library_features:
                file.write(f"    {feat}\n")

        if custom_features:
            file.write("Security_Features_Custom\n")
            for pos in custom_features:
                file.write(f"    {pos}\n")

    print(f".feature-model file created at: {feature_model_path}")


def print_top_keywords(keyword_counter, total_matches):
    """Print the top 10 keyword matches and their percentages with category and subcategory."""
    print("\nTop 10 Keyword Matches:")
    sorted_keywords = keyword_counter.most_common(10)
    for (category, subcategory, keyword), count in sorted_keywords:
        percentage = (count / total_matches) * 100
        print(f"{category} : {subcategory} : {keyword}: {count} matches ({percentage:.2f}%)")


def main():
    repo_url = input("Enter the repository URL: ")
    keyword_file = "SecList.json"
    features_file = "features.json"

    # Clone the repository
    project_dir, repo_name = clone_repository(repo_url)

    # Process library annotations first
    library_features = set()
    process_feature_annotations(features_file, project_dir, library_features)

    # Load keywords
    with open(keyword_file, "r") as file:
        keyword_dict = json.load(file)
    flattened_keywords = flatten_keywords(keyword_dict)

    # Initialize the exclusion counter ONCE here
    hans_exclusion_counter = [0]

    # Search for keywords in the codebase
    results, custom_features, keyword_counter = search_codebase(project_dir, flattened_keywords, hans_exclusion_counter)

    # Save the results to a JSON file
    save_results_to_json(repo_name, results)

    # Create the .feature-model file
    create_feature_model_file(project_dir, custom_features, library_features)

    # Count the number of unique security feature locations**
    total_security_features = len(custom_features)  # Count unique PosX entries

    # Count the number of files with at least one match
    total_files_with_matches = len(results)

    # Print the number of previously annotated Features
    print(f"Number of Features Identified by the Automated Tool: {hans_exclusion_counter[0]} Features")

    # Print the summary
    print(f"Number of Security Feature Possible Locations: {total_security_features}")
    print(f"Number of Files with Matches: {total_files_with_matches}")

    # Print the top 10 keyword matches
    print_top_keywords(keyword_counter, total_security_features)


if __name__ == "__main__":
    main()
