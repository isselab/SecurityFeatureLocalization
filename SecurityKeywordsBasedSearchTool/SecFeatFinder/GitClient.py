import os
import subprocess


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
            subprocess.run(["git", "clone", repo_link, project_dir],
                           check=True)
            print(f"Cloned '{project_name}' into 'repos'.")
        except subprocess.CalledProcessError as e:
            raise RuntimeError(f"Failed to clone repository: {e}")
    return project_dir, project_name
