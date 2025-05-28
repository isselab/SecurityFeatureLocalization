class Feature:
    def __init__(self, name, parent):
        self.name = name
        self.parent = parent
        self.sub_features = []
        if parent is not None:
            parent.sub_features.append(self)

    # Searches in the feature tree for a feature with the given name
    def dfs(self, name):
        stack = [self]
        while len(stack) > 0:
            feature = stack.pop()
            if feature.name == name:
                return feature
            stack.extend(feature.sub_features)

    def __str__(self):
        return "Feature: " + self.name
