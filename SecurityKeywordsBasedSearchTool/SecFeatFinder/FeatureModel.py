import os

from Feature import Feature


def add_to_fm(fm, taxonomy, feature_name, tag):
    taxo_feature = taxonomy.dfs(feature_name)

    feature = taxo_feature
    parents = [feature]
    while feature.parent is not None:
        feature = feature.parent
        parents.append(feature)

    if parents.pop().name == fm.name:
        for feature in reversed(parents):
            exists = False
            for proposal in fm.sub_features:
                if proposal.name == feature.name:
                    fm = proposal
                    exists = True
                    break
            if not exists:
                fm = Feature(feature.name, fm)
        return Feature(tag, fm)


def create_feature_model_file(repo_dir, custom_features, library_features, fm):
    # Save the feature model structure
    feature_model_path = os.path.join(repo_dir, ".feature-model")
    with open(feature_model_path, "w") as file:
        indentation = 0
        save_feature_model(fm, file, indentation)
    print(f".feature-model file created at: {feature_model_path}")


def read_feature_model(file):
    # This function reads a feature mode from a file
    if not os.path.exists(file):
        print(f"Keywords file not found at { os.path.abspath(file)}.")
        return None

    with open(file, "r") as f:
        depths = {-1: 0}
        stack = []
        for line in f.readlines():
            indentation = 0
            for c in line:
                if c == '\t':
                    indentation += 4
                elif c == ' ':
                    indentation += 1
                else:
                    break
            name = line.strip()

            stack_depth = len(stack) - 1
            if indentation in depths.keys():
                new_feature_depth = depths[indentation]
            else:
                new_feature_depth = len(stack)
                depths[indentation] = new_feature_depth

            while new_feature_depth <= stack_depth:
                stack.pop()
                stack_depth -= 1

            if len(stack) > 0:
                parent = stack[len(stack) - 1]
            else:
                parent = None

            feature = Feature(name, parent)
            stack.append(feature)
        return stack[0]


def save_feature_model(fm, file, indentation):
    file.write("\t" * indentation + fm.name + "\n")
    for child in fm.sub_features:
        save_feature_model(child, file, indentation + 1)
