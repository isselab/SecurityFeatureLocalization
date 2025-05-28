import os
import json
import re
from collections import Counter, defaultdict

from SecurityKeywordsBasedSearchTool.SecFeatFinder.Feature import Feature
from SecurityKeywordsBasedSearchTool.SecFeatFinder.FeatureModel import add_to_fm, create_feature_model_file, \
    read_feature_model
from SecurityKeywordsBasedSearchTool.SecFeatFinder.GitClient import clone_repository


def flatten_keywords(keyword_dict):
    """Flatten the nested keyword dictionary into a list with categories."""
    flattened = []
    for category, subcategories in keyword_dict.items():
        if isinstance(subcategories, dict):
            for subcategory, keywords in subcategories.items():
                for keyword in keywords:
                    flattened.append((category, subcategory, keyword))
        elif isinstance(subcategories, list):
            # For keywords directly under a category
            for keyword in subcategories:
                flattened.append((category, "Miscellaneous", keyword))
    return flattened


def process_feature_annotations(features_file, repo_dir, flattened_keywords, taxonomy, fm):
    if os.path.exists(features_file):
        with open(features_file, "r") as file:
            data = json.load(file)
    else:
        data = {}

    library_features = set()

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
                        tag = f"API_{feature_name}_{method_name}"
                        line_annotations[line_index].add(tag)
                        library_features.add(tag)
                        add_to_fm(fm, taxonomy, feature_name, tag)

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
    return library_features


def get_subtree(flattened_keywords, feature_name):
    """Search in the flattened keywords for the given feature name and return the result."""
    for category, subcategory, keyword in flattened_keywords:
        if feature_name.lower() in keyword.lower():
            return category, subcategory, keyword
    return feature_name


def search_keywords_in_file(file_path, flattened_keywords, repo_dir,
                            pos_counter, pos_list, keyword_counter,
                            hans_exclusion_counter, fm):
    """
    Search for keywords in a given file and return matches,
    excluding comments, test-related code, and HAnS-annotated code.
    """
    matches = {}
    short_path = os.path.relpath(file_path, repo_dir)
    if "src\\" in short_path:
        short_path = short_path[short_path.index("src\\"):]
    updated_lines = []  # Store updated lines with added comments
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
                features, fm = determine_feature(pos_counter, matches, line_number, fm)

                comment_start = "// &begin["+features+"]\n"
                updated_lines.append(comment_start)
                updated_lines.append(line)  # Add the line with the match
                comment_end = "// &end["+features+"]\n"
                updated_lines.append(comment_end)
                pos_list.append(f"Pos{pos_counter[0]}")

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


def determine_feature(pos_counter, matches, line_number, fm):
    features = ''
    for match in list(matches[line_number]["Keywords Found"].keys()):
        if len(features) > 0:
            features += ', '
        path = match.split(' : ')
        length = len(path)
        feature = 'KeywordMatch_' + str(pos_counter[0]) + '_' + path[length - 1]
        pos_counter[0] += 1
        features += feature

        current = fm
        i = 0
        while i < length:
            name = path[i]
            found = False
            for sub in current.sub_features:
                if name == sub.name:
                    current = sub
                    found = True
                    break
            if not found:
                current = Feature(name, current)
            i += 1
        Feature(feature, current)
    return features, fm


def search_codebase(temp_dir, flattened_keywords, hans_exclusion_counter, fm):
    """
    Search the entire codebase for security keywords and group matches by file.
    """
    results = []

    # Counter for the global position numbers, use array to pass the value
    pos_counter = [1]

    pos_list = []  # List to store all PosX values

    # Counter to track keyword occurrences, including category and subcategory
    keyword_counter = Counter()

    # Walk through the directory and process each Java file
    for root, _, files in os.walk(temp_dir):
        for file in files:
            if file.endswith(".java"):
                file_path = os.path.join(root, file)
                if "test" not in file_path.lower():
                    file_name, short_path, matches = search_keywords_in_file(
                        file_path, flattened_keywords, temp_dir, pos_counter,
                        pos_list, keyword_counter, hans_exclusion_counter, fm)
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
    taxonomy_file = "taxonomy.feature_model"

    taxonomy = read_feature_model(taxonomy_file)

    # Clone the repository
    project_dir, repo_name = clone_repository(repo_url)

    # Load keywords
    with open(keyword_file, "r") as file:
        keyword_dict = json.load(file)
    flattened_keywords = flatten_keywords(keyword_dict)

    # int fm
    fm = Feature(taxonomy.name, None)

    # Process library annotations first
    library_features = process_feature_annotations(features_file, project_dir, flattened_keywords, taxonomy, fm)

    # Initialize the exclusion counter ONCE here
    hans_exclusion_counter = [0]

    # Search for keywords in the codebase
    results, custom_features, keyword_counter = search_codebase(project_dir, flattened_keywords, hans_exclusion_counter, fm)

    # Save the results to a JSON file
    save_results_to_json(repo_name, results)

    # Create the .feature-model file
    create_feature_model_file(project_dir, custom_features, library_features, fm)

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
