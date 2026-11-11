from pathlib import Path
p=Path(__file__).parent
for page,template in [('index.html','page-template.txt'),('comparison.html','comparison-template.txt')]:
    (p/page).write_text((p/template).read_text(),encoding='utf-8')
