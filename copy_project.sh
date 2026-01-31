find "$PWD" -type f \
  -not -path "*/.git/*" \
  -not -path "*/target/*" \
  -not -path "*/.idea/*" \
  -not -path "*/test/*" \
  -not -name "pom.xml" \
  -not -name "*.txt" \
  -not -name "*.md" \
  -not -name "*.yaml" \
  -not -name "*.yml" \
  -not -name "*.xml" \
  -exec sh -c '
    for f do
      printf "\n\n===== %s =====\n\n" "$f"
      cat "$f"
    done
  ' sh {} + > project_dump.txt