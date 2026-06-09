import csv
import os
from jinja2 import Environment, FileSystemLoader

# Directories
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
OUTPUT_DIR = os.path.abspath(os.path.join(BASE_DIR, "../../websites/compare/public/"))
DATA_FILE = os.path.join(BASE_DIR, "data.csv")

def ensure_output_dir():
    if not os.path.exists(OUTPUT_DIR):
        os.makedirs(OUTPUT_DIR)

def generate_pages():
    ensure_output_dir()
    
    # Set up Jinja2 environment
    env = Environment(loader=FileSystemLoader(BASE_DIR))
    template = env.get_template('template.html')

    print(f"Reading data from {DATA_FILE}...")
    
    try:
        with open(DATA_FILE, 'r', encoding='utf-8') as f:
            reader = csv.DictReader(f)
            count = 0
            for row in reader:
                # Process lists (pros/cons are separated by '|')
                context = {
                    'slug': row['slug'],
                    'category': row['category'],
                    'winner': row['winner'],
                    'affiliate_link': row['affiliate_link'],
                    
                    'tool1_name': row['tool1_name'],
                    'tool1_logo': row['tool1_logo'],
                    'tool1_price': row['tool1_price'],
                    'tool1_pros': row['tool1_pros'].split('|'),
                    'tool1_cons': row['tool1_cons'].split('|'),
                    
                    'tool2_name': row['tool2_name'],
                    'tool2_logo': row['tool2_logo'],
                    'tool2_price': row['tool2_price'],
                    'tool2_pros': row['tool2_pros'].split('|'),
                    'tool2_cons': row['tool2_cons'].split('|'),
                }
                
                # Render HTML
                html_out = template.render(context)
                
                # Save to output folder
                output_path = os.path.join(OUTPUT_DIR, f"{row['slug']}.html")
                with open(output_path, 'w', encoding='utf-8') as out_f:
                    out_f.write(html_out)
                    
                print(f"Generated: {output_path}")
                count += 1
                
        print(f"\nSuccess! Generated {count} pages.")
        
    except Exception as e:
        print(f"Error during generation: {e}")

if __name__ == "__main__":
    generate_pages()
