#!/bin/bash
# Render.com build script for JobTracker
# Installs LaTeX and builds the application

set -e  # Exit on error

echo "📦 Installing LaTeX packages..."

# Install minimal LaTeX distribution for PDF generation
apt-get update -qq
apt-get install -y --no-install-recommends \
    texlive-latex-base \
    texlive-latex-extra \
    texlive-fonts-recommended

echo "✅ LaTeX packages installed"

# Verify pdflatex is available
if command -v pdflatex &> /dev/null; then
    echo "✅ pdflatex is available:"
    pdflatex --version | head -1
else
    echo "❌ pdflatex not found!"
    exit 1
fi

echo "🔨 Building application with Maven..."

# Build the application
./mvnw clean package -DskipTests

echo "✅ Build complete!"
