from pathlib import Path
p=Path(r'app/src/main/java/com/huiyang/quizlocktv/MainActivity.kt')
print(p.read_text(encoding='utf-8'))
