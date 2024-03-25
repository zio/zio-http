#!/bin/bash

# Check if correct number of arguments are provided
if [ "$#" -ne 2 ]; then
    echo "Usage: $0 <base_file> <current_file>"
    exit 1
fi

base_file=$1
current_file=$2

if [ ! -f "$base_file" ]; then
    echo "Base file not found: $base_file"
    exit 1
fi

if [ ! -f "$current_file" ]; then
    echo "Current file not found: $current_file"
    exit 1
fi

markdown_file="benchmarks.md"

# Write Markdown table header
echo "| Benchmark | Base | Current | % Change | Unit |" > "$markdown_file"
echo "| --------- | ---- | ------- | -------- | ---- |" >> "$markdown_file"

# Process each line of the base file
while IFS= read -r base_line && IFS= read -r current_line <&3; do
    # Extract benchmark name, base value, and unit from base line
    base_benchmark=$(echo "$base_line" | cut -d ':' -f 1)
    base_value=$(echo "$base_line" | awk '{print $2}')
    unit=$(echo "$base_line" | awk '{print $3}')

    # Extract current value from current line
    current_value=$(echo "$current_line" | awk '{print $2}')

    # Calculate the difference and percentage change
    difference=$(echo "$current_value - $base_value" | bc)
    percent_change=$(echo "scale=2; ($difference / $base_value) * 100" | bc)

    # Write to Markdown file
    echo "| $base_benchmark | $base_value | $current_value | $percent_change% | $unit |" >> "$markdown_file"
done < "$base_file" 3< "$current_file"

echo "Markdown file generated: $markdown_file"
