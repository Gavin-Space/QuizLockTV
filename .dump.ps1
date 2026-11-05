from pathlib import Path
for f in Path('app/src/main/java/com/huiyang/quizlocktv').glob('*.kt'):
 print('\n===== '+str(f)+' =====')
 print(f.read_text(encoding='utf-8-sig'))
