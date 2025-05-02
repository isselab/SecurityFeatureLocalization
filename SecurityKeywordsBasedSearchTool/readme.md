
# Keyword-Based Search Tool

This tool is designed to semi-automate the detection of potential security features in Java software projects using a keyword-based approach.

---

## 1. Repository Cloning

First, you need to clone the repository you want to analyze locally.

---

## 2. Integration with API-Call-Based Tool

- Start by running the Security Features API Automated Detection Tool. Navigate to the tool's path:
  ```bash
  cd security-feature-mining-study\SecurityFeatureMiningStudy\security-feature-localization
  ```

- Then, run the following command:
  ```bash
  java -jar target/security-feature-localization.jar locate "Cloned_Repo_Path" --mappings "security-feature-mining-study\Resources\lib-mappings"
  ```

- You will find a JSON file named `features.json` generated in the `result` directory within the cloned repository.

- Copy that file into the main script directory `SecFeatFinder`.

---

## 3. Running the Tool

Run the script with a GitHub repository URL. The tool will:

- Clone the repository into a local `repos/` folder, if it was not already cloned in the previous step.
- Extract the project name from the URL.
- Ensure no duplicate cloning occurs.

This step is handled by the `clone_repository()` function.

---

## 4. Security Keywords List Modification

Security keywords are maintained in a structured JSON file named `SecList.json`.  
You can update the keywords for each category during the validation process.

---

## 5. Output Insights

After running the script, it provides initial insights such as:

- Total keyword matches.
- Number of affected files.
- Number of confidently identified features.
- Top 10 matched keywords.

These insights help prioritize and guide the manual validation phase.  
In addition to annotations for potential security feature locations, a JSON file is generated with the same name as the analyzed repository, containing all detected positions.

---

## 6. Embedded Feature Annotations

The tool automatically generates annotations in a `.feature-model` file at the root of the project.

### Annotation Format:
- Uses generic tags like `Pos1`, `Pos2`, ...
- Compatible with the HAnS plugin in IntelliJ IDEA.

This enables fast, structured manual inspection of flagged code segments.

---

## 7. Feature Classification

Identified security features should be organized into three categories:

- `Security_Features_Custom` – Developer-implemented features.
- `Security_Features_Library` – Features from third-party libraries.
- `Security_Features_Library_Tool` – Features detected by the API-call-based tool.

Each category is further organized by taxonomy category and subcategory.

---

## 8. Extending to More Identified Libraries

If you identify security features from a library or framework that you want the tool to automatically recognize, you can add them to the `security-feature-mining-study\Resources\lib-mappings\` directory.

Simply create a new JSON file using any existing one as a template.

---

### Notes:
- Ensure Python 3 is installed.
- The script is tailored exclusively for Java projects.
- Use the `.feature-model` output with the HAnS plugin for manual review.