#!/bin/bash

# ANSI color codes
RED='\033[0;31m' # Red color
NC='\033[0m'     # No color

if [ "$#" -ne 2 ]; then
    echo "Usage: $0 <base_file> <current_file>" >&2
    exit 1
fi

base_file=$1
current_file=$2

if [ ! -f "$base_file" ]; then
    echo "Base file not found: $base_file" >&2
    exit 1
fi

if [ ! -f "$current_file" ]; then
    echo "Current file not found: $current_file" >&2
    exit 1
fi

markdown_file="benchmark.md"

echo "| Benchmark | Base | Current | % Change | Unit |" > "$markdown_file"
echo "| --------- | ---- | ------- | -------- | ---- |" >> "$markdown_file"

# Flag to check if any change exceeds 10%
change_exceeds=false

# Process each line of the base file
while IFS= read -r base_line && IFS= read -r current_line <&3; do
    # Extract benchmark name, base value, and unit from base line
    base_benchmark=$(echo "$base_line" | cut -d ':' -f 1)
    base_value=$(echo "$base_line" | awk '{print $2}')
    unit=$(echo "$base_line" | awk '{print $3}')

    # Extract current value from current line
    current_value=$(echo "$current_line" | awk '{print $2}')

    # Calculate the percentage change
    percent_change=$(echo "scale=2; (($current_value - $base_value) / $base_value) * 100" | bc)

    # Write to Markdown file
    echo "| $base_benchmark | $base_value | $current_value | $percent_change% | $unit |" >> "$markdown_file"

    # Check if percentage change exceeds 10%
    if (( $(echo "$percent_change > 10" | bc -l) )); then
        # Print benchmark in red color
        echo -e "${RED}$base_benchmark has a percentage change of: $percent_change%${NC}" >&2
        change_exceeds=true
    fi
done < "$base_file" 3< "$current_file"

if [ "$change_exceeds" = true ]; then
    cat "$markdown_file"
    echo "CI failing as above benchmarks exceeds 10% threshold." >&2
    exit 1
fi

