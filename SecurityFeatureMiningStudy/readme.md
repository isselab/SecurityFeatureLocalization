# Security Feature Analysis Project

This project is designed to analyze Java repositories for security feature usage. It is divided into four sub-modules:

---

## 1. Repository Mining

The **Repository Mining** module mines Java repositories from GitHub and stores the relevant data in a PostgreSQL database.

### Requirements:

Set the following environment variables before running this module:

- `DB_URL` - The URL to the PostgreSQL database.
- `DB_USER` - The database user.
- `DB_PASSWORD` - The password for the database user.
- `GITHUB_OAUTH` - A GitHub OAuth token to access the GitHub API.

---

## 2. Security Feature Localization

The **Security Feature Localization** module extracts security feature usage from Java codebases. It can be used as a library or via CLI commands.

### CLI Commands:

1. **Locate Features:**

   ```bash
   locate PROJECT_DIR --mappings MAPPINGS_DIR
   ```

   This command generates a JSON file containing feature information in the project directory.

2. **Annotate Source Code:**
   ```bash
   annotate PROJECT_DIR --mappings MAPPINGS_DIR
   ```
   This command creates HAnS feature annotations directly within the source code files.

---

## 3. Security Feature Mining

The **Security Feature Mining** module automatically downloads repositories gathered by the Repository Mining module, extracts security features, and stores the data in a PostgreSQL database.

### Requirements:

Set the following environment variables before running this module:

- `DB_URL` - The URL to the PostgreSQL database.
- `DB_USER` - The database user.
- `DB_PASSWORD` - The password for the database user.

Additionally, Java, Maven and Gradle must be installed.
For more accurate results, it is recommended to download the most common Java JDK versions.
The JDK directories must follow the naming convention `jdk-[version number]` to be included in the analysis.

---

## 4. Metric Calculations

The **Metric Calculations** module computes various metrics based on the security features extracted by the Security Feature Mining module.

### Requirements:

Set the following environment variables before running this module:

- `DB_URL` - The URL to the PostgreSQL database.
- `DB_USER` - The database user.
- `DB_PASSWORD` - The password for the database user.

Additionally, a folder containing the JSON library metrics, named `lib-mappings`, must be present in the current working directory.

---

### Notes:

- Make sure the required environment variables are properly set for each module.
- This project requires access to a PostgreSQL database and GitHub API (for Repository Mining).
