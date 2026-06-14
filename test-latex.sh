#!/bin/bash
# Quick LaTeX API Test Script
# Tests if YtoTech API is working correctly

echo "🧪 Testing LaTeX API..."
echo ""

# Test with minimal LaTeX document
curl -X POST https://ytotech.com \
  -H "Content-Type: application/json" \
  -d '{
    "compiler": "pdflatex",
    "resources": [{
      "name": "main.tex",
      "content": "\\documentclass{article}\\begin{document}\\title{Test}\\author{JobTracker}\\maketitle\\section{Test Section}This is a test.\\end{document}"
    }]
  }' \
  --silent \
  --output test_latex_output.pdf

echo "✅ API call completed"
echo ""

# Check what we got
FILE_TYPE=$(file test_latex_output.pdf)

if [[ $FILE_TYPE == *"PDF"* ]]; then
    FILE_SIZE=$(stat -f%z test_latex_output.pdf 2>/dev/null || stat -c%s test_latex_output.pdf 2>/dev/null)
    echo "✅ SUCCESS! LaTeX API is working!"
    echo "   File: test_latex_output.pdf"
    echo "   Size: $FILE_SIZE bytes"
    echo "   Type: PDF document"
    echo ""
    echo "📄 Open the PDF to verify:"
    echo "   open test_latex_output.pdf  (Mac)"
    echo "   xdg-open test_latex_output.pdf  (Linux)"
else
    echo "❌ FAILED! API returned HTML instead of PDF"
    echo "   File type: $FILE_TYPE"
    echo ""
    echo "📋 First 500 chars of response:"
    head -c 500 test_latex_output.pdf
    echo ""
    echo ""
    echo "This means LaTeX compilation failed on YtoTech's side."
fi

echo ""
