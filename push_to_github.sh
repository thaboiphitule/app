#!/bin/bash
# A script to push your Hermes Companion Agent App to your new GitHub repository!

echo "=========================================================="
echo "🚀 Push Hermes Companion Agent to GitHub"
echo "=========================================================="

# Prompt user for their repository URL
read -p "Enter your GitHub Repository URL (e.g. https://github.com/username/repo-name.git): " repo_url

if [ -z "$repo_url" ]; then
    echo "❌ Error: Repository URL cannot be empty."
    exit 1
fi

# Set remote origin
git remote remove origin 2>/dev/null
git remote add origin "$repo_url"

# Ensure the branch is renamed to main (GitHub standard default)
git branch -M main

echo "⏳ Pushing codebase and compiled app-debug.apk to GitHub..."
git push -u origin main

if [ $? -eq 0 ]; then
    echo "✅ Success! Your companion agent code and APK are now live on your GitHub!"
else
    echo "❌ Push failed. Please make sure your repository is empty or check your GitHub permissions (such as Personal Access Token or SSH keys)."
fi
