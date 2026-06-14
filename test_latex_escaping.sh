#!/bin/bash
# Quick test for LaTeX escaping fix

echo "Testing LaTeX escaping for problematic content..."

# Create a minimal test LaTeX file with the problematic pattern
cat > /tmp/test_resume.tex << 'EOF'
\documentclass{article}
\begin{document}

\newcommand{\resumeItem}[1]{\item #1}

\begin{itemize}
  \resumeItem{Developed RESTful APIs serving 100M+ users with 99.95\% uptime and sub-200ms P95 latency}
  \resumeItem{Architected distributed billing microservices handling 500K+ transactions daily with P99 latency of 120ms}
  \resumeItem{Reduced transaction processing latency by 40\% (2.5s → 1.5s P99) by implementing Redis caching}
  \resumeItem{Designed event-driven Kafka pipeline processing 10K QPS with guaranteed delivery}
\end{itemize}

\end{document}
EOF

echo "Compiling test LaTeX file..."
cd /tmp
pdflatex -interaction=nonstopmode test_resume.tex > /dev/null 2>&1

if [ $? -eq 0 ]; then
    echo "✅ SUCCESS! LaTeX compilation passed"
    echo "PDF created: /tmp/test_resume.pdf ($(stat -f%z /tmp/test_resume.pdf 2>/dev/null || stat -c%s /tmp/test_resume.pdf) bytes)"

    # Cleanup
    rm -f test_resume.tex test_resume.pdf test_resume.aux test_resume.log
    exit 0
else
    echo "❌ FAILED! LaTeX compilation error"
    echo ""
    echo "Error output:"
    tail -20 test_resume.log
    exit 1
fi
